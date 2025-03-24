# 脉动阵列矩阵乘法器
操作单元FPMAC 浮点乘加器 FPMAC_5S

乘法器，加法器实现原理
参考连接：https://blog.csdn.net/weixin_58275336/article/details/136738605

Stage1: 乘法计算

Stage2: 乘法归一化输出

Stage3: 加法对齐

Stage4: 加法计算

Stage5: 结果组装输出

## IO

    val input = Input(UInt(TOTAL_WIDTH.W))
    val weight = Input(UInt(TOTAL_WIDTH.W))
    val psum = Input(UInt(TOTAL_WIDTH.W))
    val out = Output(UInt(TOTAL_WIDTH.W))
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())