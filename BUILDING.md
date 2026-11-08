# 构建环境

本仓库保存 Fabric 服务端移植源码、固定上游源码和资源覆盖。`prepare.cjs`、`adapt.cjs` 是首次导入记录，请勿在当前源码上重新运行。

现有脚本依赖原 Timory 工作区的布局，仓库本身不包含 Minecraft 服务端/客户端分发文件、运行存档或预下载的开发依赖。构建前需准备以下父目录：

```text
<workspace>/
  riichi-mahjong-fabric/                 本仓库中的同名目录
  build/tools/jdk-25.0.4.1+1/bin/        Java 25 的 java、javac、jar 工具
  build/kaleidoscope/classpath.json      Polymer 依赖 classpath JSON 数组
  build/kaleidoscope/runtime/            Polymer bundled 0.18.7+26.3 及提取的嵌套依赖
  run/crop-26.3/versions/26.3/           server-26.3.jar
  run/crop-26.3/libraries/               服务端依赖
  run/crop-26.3/.fabric/processedMods/   Fabric Loader/API 处理后的依赖
  TimoryResource/                       资源包工作目录
  deploy/TimoryGameplay-2.7.18+26.3/     合并资源包的基准 TimoryResource-26.3.zip
```

源码仓库 checkout 的外层目录名可自由选择，复制其 `riichi-mahjong-fabric/` 子目录到准备好的工作区后，从工作区根目录运行：

```powershell
node riichi-mahjong-fabric/resources.cjs
node riichi-mahjong-fabric/build.cjs
node riichi-mahjong-fabric/package.cjs
```

`resources.cjs` 从固定上游复制资源，应用简体中文覆盖及 26.3 进度背景路径修正，并生成原版显示入口；`build.cjs` 编译移植和原规则库；`package.cjs` 合并 TimoryResource 并生成附件及校验值。

资源包基准只影响合并打包，麻将模型和材质本身也保存在 `src/main/resources/assets/riichi_mahjong/`。`install-local.cjs` 面向原工作区的本地测试服管理器，一般服务器请直接按 README 安装 Release 附件。
