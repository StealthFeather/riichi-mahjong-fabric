package com.riichimahjong.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** A persistent JSON-lines process, serialized by its owning executor. */
final class AiWorker {
    @FunctionalInterface interface Launcher { Process start() throws Exception; }
    private final Launcher launcher;
    private final String expectedEngine;

    AiWorker(Launcher launcher, String expectedEngine) {
        this.launcher = launcher;
        this.expectedEngine = expectedEngine;
    }

    private volatile Process process;
    private BufferedReader output;
    private BufferedWriter input;
    private long activeId = -1;

    int request(JsonObject snapshot, BooleanSupplier done) throws Exception {
        if (done.getAsBoolean()) throw new IOException("Decision cancelled");
        long id = snapshot.get("id").getAsLong();
        synchronized (this) { activeId = id; }
        try {
            Process current = process;
            if (current == null || !current.isAlive()) {
                Process started = launcher.start();
                synchronized (this) {
                    // A cancelled launch must never publish its process into a later request.
                    if (activeId != id || done.getAsBoolean()) { started.destroyForcibly(); throw new IOException("Decision cancelled"); }
                    process = started;
                    output = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8));
                    input = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
                }
            }
            if (done.getAsBoolean()) { cancel(id); throw new IOException("Decision cancelled"); }
            input.write(snapshot.toString()); input.newLine(); input.flush();
            String line = readLine(output);
            if (line == null) throw new IOException("AI worker exited without a response");
            JsonObject reply = JsonParser.parseString(line).getAsJsonObject();
            if (!reply.has("id") || reply.get("id").getAsBigDecimal().longValueExact() != snapshot.get("id").getAsLong()) throw new IOException("AI response id mismatch");
            if (reply.has("error")) throw new DecisionError(reply.get("error").getAsString());
            if (!reply.has("index") || reply.get("index").isJsonNull()) throw new IOException("AI response missing index");
            if (expectedEngine != null && !(reply.has("engine") && expectedEngine.equals(reply.get("engine").getAsString())))
                throw new IOException("AI response engine mismatch");
            return reply.get("index").getAsBigDecimal().intValueExact();
        } finally {
            synchronized (this) { if (activeId == id) activeId = -1; }
        }
    }

    synchronized void cancel(long id) { if (activeId == id) { activeId = -1; kill(); } }
    synchronized void kill() { Process current = process; process = null; if (current != null) current.destroyForcibly(); }

    static final class DecisionError extends IOException {
        DecisionError(String message) { super(message); }
    }

    private static String readLine(BufferedReader reader) throws IOException {
        var line = new StringBuilder();
        for (int next; (next = reader.read()) >= 0;) {
            if (next == '\n') return line.toString();
            if (next != '\r') line.append((char) next);
            if (line.length() > 65_536) throw new IOException("AI worker response exceeded limit");
        }
        return line.isEmpty() ? null : line.toString();
    }
}
