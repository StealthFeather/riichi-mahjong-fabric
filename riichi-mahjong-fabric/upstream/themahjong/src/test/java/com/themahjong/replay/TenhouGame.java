package com.themahjong.replay;

import java.util.List;

public record TenhouGame(String gameId, List<TenhouRound> rounds) {}
