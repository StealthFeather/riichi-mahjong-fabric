# Mortal 四麻推理进程

安装器面向 Linux x86_64、glibc 2.28+ 和带 venv 的 CPython 3.12。下面的目标目录应位于 Minecraft 服务端工作目录下。安装期间从 GitHub 和 PyPI 下载固定版本的文件，校验 SHA-256，再用真实权重执行自检。对局期间在服务端 CPU 本地推理。

```sh
python3.12 ai/mortal/install.py /path/to/server/config/riichi-mahjong-ai/mortal
```

如果使用其他安装目录，将 `RIICHI_MORTAL_WORKER` 或 JVM 属性 `riichi.mortal.worker` 指向安装目录下的 `worker`。手动重跑自检可以使用以下命令。

```sh
python3.12 ai/mortal/smoke_test.py /path/to/server/config/riichi-mahjong-ai/mortal/worker
```

采用社区导出的四麻 Mortal v4 网络 `mortal_best.onnx`（40 个残差块、192 通道），以及相同仓库中的 libriichi Python 扩展。上游 Mortal 代码采用 AGPL-3.0-or-later。模型来源最初报告了 PyTorch 和 ONNX 的输出对照，安装器固定到 `f2dc4603d3fed49727be0ba053d7ffa93de67891`。权重和原生库单独下载。[社区模型及导出说明](https://github.com/lietxia/my_mortal/tree/f2dc4603d3fed49727be0ba053d7ffa93de67891/step2) · [Mortal 上游](https://github.com/Equim-chan/Mortal)

每行请求是一个独立的座位视角。进程保留模型，并为每次请求按顺序重放已脱敏的本局 MJAI 事件。ONNX 在 Java 合法行动与 libriichi 可选行动的交集中选分数最高的行动。立直包含第二次推理，用来选择宣言牌。无共同动作或遇到 libriichi 无法解析的局面时返回错误，由调用方处理。东风战沿用同一权重，只调整网络中的局程进度，东场役、座风及合法行动仍按实际牌局计算。南入延长局按终盘处理。东风战策略强度尚未独立评测。
