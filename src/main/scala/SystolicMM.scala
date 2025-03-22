package fpmac

import chisel3._
import chisel3.util._

// 单个处理单元（PE）的定义
class PEFp(useHalf: Boolean) extends Module {
  val TOTAL_WIDTH = if (useHalf) 16 else 32
  val io = IO(new Bundle {
    // 数据输入
    val a_in = Input(UInt(TOTAL_WIDTH.W))
    val b_in = Input(UInt(TOTAL_WIDTH.W))
    // 数据输出（传递给下一个PE）
    val a_out = Output(UInt(TOTAL_WIDTH.W))
    val b_out = Output(UInt(TOTAL_WIDTH.W))
    val out = Output(UInt(TOTAL_WIDTH.W))
    // 控制信号
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())
    val reset = Input(Bool())
  })

  // 实例化FPMAC
  val fpmac = Module(new FPMAC(useHalf))
  val res = RegInit(0.U(TOTAL_WIDTH.W)) // 保有的部分和

  // 连接FPMAC
  fpmac.io.input := io.a_in
  fpmac.io.weight := io.b_in
  fpmac.io.psum := res
  fpmac.io.valid_in := io.valid_in

  // 数据传递，添加寄存器延迟以匹配计算延迟
  val a_regs = RegInit(VecInit(Seq.fill(5)(0.U(TOTAL_WIDTH.W))))
  val b_regs = RegInit(VecInit(Seq.fill(5)(0.U(TOTAL_WIDTH.W))))
  val valid_regs = RegInit(VecInit(Seq.fill(5)(false.B)))
  
  // 移位寄存器实现延迟
  a_regs(0) := io.a_in
  b_regs(0) := io.b_in
  valid_regs(0) := io.valid_in
  
  for (i <- 1 until 5) {
    a_regs(i) := a_regs(i-1)
    b_regs(i) := b_regs(i-1)
    valid_regs(i) := valid_regs(i-1)
  }
  
  // 输出连接到延迟后的数据
  io.a_out := a_regs(4)
  io.b_out := b_regs(4)
  io.valid_out := fpmac.io.valid_out

  // 结果处理
  when(io.reset) {
    res := 0.U
  }.elsewhen(fpmac.io.valid_out) {
    res := fpmac.io.out
  }
  
  io.out := res
}

// 脉动阵列矩阵乘法器
class SystolicMM(val n: Int, val useHalf: Boolean) extends Module {
  val TOTAL_WIDTH = if (useHalf) 16 else 32

  val io = IO(new Bundle {
    val a_inputs = Input(Vec(n, UInt(TOTAL_WIDTH.W)))
    val b_inputs = Input(Vec(n, UInt(TOTAL_WIDTH.W)))
    val output_matrix = Output(Vec(n, Vec(n, UInt(TOTAL_WIDTH.W))))
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())
    val reset = Input(Bool())
  })

  // 创建PE阵列
  val pes = VecInit(
    Seq.tabulate(n)(i =>
      VecInit(Seq.tabulate(n)(j => Module(new PEFp(useHalf)).io))
    )
  )

  // 连接PE阵列
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      pes(i)(j).reset := io.reset
      io.output_matrix(i)(j) := pes(i)(j).out
      
      // 第一行PE的输入连接，按列输入b
      if (i == 0) {
        pes(i)(j).b_in := io.b_inputs(j)
      } else {
        pes(i)(j).b_in := pes(i - 1)(j).b_out
      }
      
      // 第一列PE的输入连接, 按行输入a
      if (j == 0) {
        pes(i)(j).a_in := io.a_inputs(i)
      } else {
        pes(i)(j).a_in := pes(i)(j - 1).a_out
      }
      
      // valid信号传递逻辑修正
      if (i == 0 && j == 0) {
        // 左上角PE直接从输入获取valid信号
        pes(i)(j).valid_in := io.valid_in
      } else if (i == 0) {
        // 第一行其他PE从左侧PE获取valid信号
        pes(i)(j).valid_in := pes(i)(j-1).valid_out
      } else if (j == 0) {
        // 第一列其他PE从上方PE获取valid信号
        pes(i)(j).valid_in := pes(i-1)(j).valid_out
      } else {
        // 其他PE需要左侧和上方PE的valid信号都为true
        pes(i)(j).valid_in := pes(i-1)(j).valid_out && pes(i)(j-1).valid_out
      }
    }
  }
  
  // 输出有效信号 - 当最后一个PE的输出有效时，整个矩阵乘法结果有效
  io.valid_out := pes(n-1)(n-1).valid_out
}

// Compute A * B, where A and B are both square matrix.
class GEMM(val n: Int, val useHalf: Boolean) extends Module {

  val TOTAL_WIDTH = if (useHalf) 16 else 32

  val io = IO(new Bundle {
    val in_a = Flipped(Decoupled(Vec(n * n, UInt(TOTAL_WIDTH.W))))
    val in_b = Flipped(Decoupled(Vec(n * n, UInt(TOTAL_WIDTH.W))))
    val out = Decoupled(Vec(n, Vec(n, UInt(TOTAL_WIDTH.W))))
    val reset = Input(Bool())
  })

  val dataValid = io.in_a.valid && io.in_b.valid

  val busy = RegInit(false.B)
  val computing = RegInit(false.B)

  io.in_a.ready := !busy
  io.in_b.ready := !busy

  val matrixAReg = RegInit(
    VecInit.fill(n)(VecInit.fill(n)(0.U(TOTAL_WIDTH.W)))
  )
  val matrixBReg = RegInit(
    VecInit.fill(n)(VecInit.fill(n)(0.U(TOTAL_WIDTH.W)))
  )

  val sysmm = Module(new SystolicMM(n, useHalf))
  sysmm.io.reset := io.reset
  sysmm.io.valid_in := computing
  
  for (i <- 0 until n) {
    sysmm.io.a_inputs(i) := 0.U
    sysmm.io.b_inputs(i) := 0.U
  }

  when(dataValid && !busy) {
    for (i <- 0 until n) {
      for (j <- 0 until n) {
        matrixAReg(i)(j) := io.in_a.bits(i * n + j)
        matrixBReg(i)(j) := io.in_b.bits(i * n + j)
      }
    }
    busy := true.B
    computing := true.B
  }

  val resValid = RegInit(false.B)
  io.out.valid := resValid
  io.out.bits := sysmm.io.output_matrix

  // 计数器需要考虑FPMAC的5周期延迟和数据流动
  val cnt = Counter(3 * n + 5)
  
  when(busy && computing && cnt.value < (2 * n).U) {
    for (i <- 0 until n) {
      val temp = cnt.value >= i.U
      val p = Mux(temp, cnt.value - i.U, 0.U)
      when(temp && p < n.U) {
        sysmm.io.a_inputs(i) := matrixAReg(i)(p(log2Ceil(n) - 1, 0))
        sysmm.io.b_inputs(i) := matrixBReg(p(log2Ceil(n) - 1, 0))(i)
      }
    }
    cnt.inc()
  }.elsewhen(busy && cnt.value < (3 * n + 4).U) {
    // 额外的周期用于等待FPMAC完成最后的计算
    computing := cnt.value < (2 * n).U
    cnt.inc()
  }

  // 检测计算完成
  when(sysmm.io.valid_out&& cnt.value >= (3 * n - 1).U) {
    print(p"Computation completed!")
    resValid := true.B
  }

  when(cnt.value === (3 * n + 4).U) {
    // 确保所有计算都已完成
    when(resValid && io.out.ready) {
      resValid := false.B
      busy := false.B
      cnt.reset()
    }
  }
}
