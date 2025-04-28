package fpmac

import chisel3._
import chisel3.util._

// 单个处理单元（PE）的定义
class PEInt4 extends Module {
  val INPUT_WIDTH = 4 // input和weight的位宽
  val OUTPUT_WIDTH = 4 // 输出位宽

  val io = IO(new Bundle {
    // 数据输入
    val a_in = Input(UInt(INPUT_WIDTH.W))
    val b_in = Input(UInt(INPUT_WIDTH.W))
    // 数据输出（传递给下一个PE）
    val a_out = Output(UInt(INPUT_WIDTH.W))
    val b_out = Output(UInt(INPUT_WIDTH.W))
    val out = Output(UInt(OUTPUT_WIDTH.W))
    // 控制信号
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())
    val reset = Input(Bool())
  })

  // 实例化INT4MAC
  val int4mac = Module(new INT4MAC)
  val res = RegInit(0.U(OUTPUT_WIDTH.W)) // 保有的部分和

  // 连接INT4MAC
  int4mac.io.input := io.a_in
  int4mac.io.weight := io.b_in
  int4mac.io.psum := res
  int4mac.io.valid_in := io.valid_in

  io.a_out := RegNext(io.a_in) // 将输入数据传递给下一个PE
  io.b_out := RegNext(io.b_in) // 将权重数据传递给下一个PE
  io.valid_out := int4mac.io.valid_out // 输出有效信号
  // 结果处理
  when(io.reset) {
    res := 0.U
  }.elsewhen(int4mac.io.valid_out) {
    res := int4mac.io.out
    printf(p"PE计算结果: ${res}\n")
  }

  io.out := res
}

// 脉动阵列矩阵乘法器
class INT4SystolicMM(val rows: Int, val cols: Int) extends Module {
  val INPUT_WIDTH = 4 // input和weight的位宽
  val OUTPUT_WIDTH = 16 // 输出位宽

  val io = IO(new Bundle {
    // 矩阵A的输入（按行输入）
    val a_in = Input(Vec(rows, UInt(INPUT_WIDTH.W)))
    // 矩阵B的输入（按列输入）
    val b_in = Input(Vec(cols, UInt(INPUT_WIDTH.W)))
    // 结果输出
    val out = Output(Vec(rows, Vec(cols, UInt(OUTPUT_WIDTH.W))))
    // 控制信号
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())
    val reset = Input(Bool())
  })

  // 创建PE阵列
  val peArray = Array.fill(rows, cols)(Module(new PEInt4))

  // 连接PE阵列
  for (i <- 0 until rows) {
    for (j <- 0 until cols) {
      // 输入连接
      if (i == 0) {
        peArray(i)(j).io.a_in := io.a_in(j)
      } else {
        peArray(i)(j).io.a_in := peArray(i - 1)(j).io.a_out
      }

      if (j == 0) {
        peArray(i)(j).io.b_in := io.b_in(i)
      } else {
        peArray(i)(j).io.b_in := peArray(i)(j - 1).io.b_out
      }

      // 控制信号连接
      if (i == 0 && j == 0) {
        peArray(i)(j).io.valid_in := io.valid_in
      } else if (i == 0) {
        peArray(i)(j).io.valid_in := peArray(i)(j - 1).io.valid_out
      } else if (j == 0) {
        peArray(i)(j).io.valid_in := peArray(i - 1)(j).io.valid_out
      } else {
        peArray(i)(j).io.valid_in := peArray(i - 1)(j).io.valid_out && peArray(
          i
        )(j - 1).io.valid_out
      }

      peArray(i)(j).io.reset := io.reset

      // 输出连接
      io.out(i)(j) := peArray(i)(j).io.out
    }
  }

  // 输出有效信号
  io.valid_out := peArray(rows - 1)(cols - 1).io.valid_out
}

// 完整的GEMM实现
class INT4GEMM2(val rows: Int, val cols: Int) extends Module {
  val INPUT_WIDTH = 4 // input和weight的位宽
  val OUTPUT_WIDTH = 16 // 输出位宽

  val io = IO(new Bundle {
    val in_a = Flipped(Decoupled(Vec(rows * cols, UInt(INPUT_WIDTH.W))))
    val in_b = Flipped(Decoupled(Vec(rows * cols, UInt(INPUT_WIDTH.W))))
    val out = Decoupled(Vec(rows, Vec(cols, UInt(OUTPUT_WIDTH.W))))
    val reset = Input(Bool())
  })

  val dataValid = io.in_a.valid && io.in_b.valid
  val busy = RegInit(false.B)
  val computing = RegInit(false.B)

  io.in_a.ready := !busy
  io.in_b.ready := !busy

  val matrixAReg = RegInit(
    VecInit.fill(rows)(VecInit.fill(cols)(0.U(INPUT_WIDTH.W)))
  )
  val matrixBReg = RegInit(
    VecInit.fill(rows)(VecInit.fill(cols)(0.U(INPUT_WIDTH.W)))
  )

  val systolic = Module(new INT4SystolicMM(rows, cols))
  systolic.io.reset := io.reset
  systolic.io.valid_in := computing

  // 初始化输入
  for (i <- 0 until rows) {
    systolic.io.a_in(i) := 0.U
  }
  for (j <- 0 until cols) {
    systolic.io.b_in(j) := 0.U
  }

  when(dataValid && !busy) {
    // 加载矩阵数据
    for (i <- 0 until rows) {
      for (j <- 0 until cols) {
        matrixAReg(i)(j) := io.in_a.bits(i * cols + j)
        matrixBReg(i)(j) := io.in_b.bits(i * cols + j)
      }
    }
    busy := true.B
    computing := true.B
  }

  val resValid = RegInit(false.B)
  io.out.valid := resValid
  io.out.bits := systolic.io.out

  // 计数器用于控制数据流动
  val cnt = Counter(2 * (rows + cols) + 1)

  when(busy && computing && cnt.value < (rows + cols).U) {
    // 数据输入阶段
    for (i <- 0 until rows) {
      val temp = cnt.value >= i.U
      val p = Mux(temp, cnt.value - i.U, 0.U)
      when(temp && p < cols.U) {
        systolic.io.a_in(i) := matrixAReg(i)(p(log2Ceil(cols) - 1, 0))
        systolic.io.b_in(i) := matrixBReg(p(log2Ceil(cols) - 1, 0))(i)
      }
    }
    cnt.inc()
  }.elsewhen(busy && cnt.value < (2 * (rows + cols)).U) {
    // 等待计算完成
    computing := cnt.value < (rows + cols).U
    cnt.inc()
  }

  // 检测计算完成
  when(systolic.io.valid_out && cnt.value >= (rows + cols - 1).U) {
    resValid := true.B
  }

  when(cnt.value === (2 * (rows + cols)).U) {
    // 确保所有计算都已完成
    when(resValid && io.out.ready) {
      resValid := false.B
      busy := false.B
      cnt.reset()
    }
  }
}

class INT4SystolicMMv2(val n: Int = 4) extends Module {
  val INPUT_WIDTH = 4 // input和weight的位宽
  val OUTPUT_WIDTH = 4 // 输出位宽
  val io = IO(new Bundle {
    val in_a = Input(Vec(n, UInt(INPUT_WIDTH.W)))
    val in_b = Input(Vec(n, UInt(INPUT_WIDTH.W)))
    val out = Output(Vec(n * n, UInt(OUTPUT_WIDTH.W)))
    val reset = Input(Bool())
  })

  val peElements = Seq.tabulate(n * n)((i) => Module(new PEInt4()))
  peElements.foreach(_.io.reset := io.reset)

  val h_wires = Wire(Vec((n - 1) * n, UInt(OUTPUT_WIDTH.W)))
  val v_wires = Wire(Vec(n * (n - 1), UInt(OUTPUT_WIDTH.W)))

  def gethidx(r: Int, c: Int): Int =
    r * (n - 1) + c // last column is terminated
  def getvidx(r: Int, c: Int): Int = r * n + c

  // connecting PEs in a systolic manner
  // debugLog(p"pe(2,0): ${p_elems(8).in_h}, ${p_elems(8).in_v}, ${p_elems(8).out}\n")
  for (col <- 0 until n) {
    for (row <- 0 until n) {
      val pidx = row * n + col
      io.out(pidx) := peElements(pidx).io.out // results

      // wiring up PEs
      // horizontal inputs
      if (col == 0) {
        peElements(pidx).io.a_in := io.in_a(row)
      } else {
        peElements(pidx).io.a_in := h_wires(gethidx(row, col - 1))
      }
      // horizontal outputs to next PEs
      if (col < n - 1) {
        h_wires(gethidx(row, col)) := peElements(pidx).io.a_out
      }

      // vertical inputs
      if (row == 0) {
        peElements(pidx).io.b_in := io.in_b(col)
      } else {
        peElements(pidx).io.b_in := v_wires(getvidx(row - 1, col))
      }
      // vertical outputs to next PEs
      if (row < n - 1) {
        v_wires(getvidx(row, col)) := peElements(pidx).io.b_out
      }
    }
  }
}

// Compute A * B, where A and B are both square matrix.
class INT4GEMM(val n: Int = 4) extends Module {
  val INPUT_WIDTH = 4 // input和weight的位宽
  val OUTPUT_WIDTH = 4 // 输出位宽
  val PE_DELAY = 0
  val io = IO(new Bundle {
    val in_a = Flipped(Decoupled(Vec(n * n, UInt(INPUT_WIDTH.W))))
    val in_b = Flipped(Decoupled(Vec(n * n, UInt(INPUT_WIDTH.W))))
    val out = Decoupled(Vec(n * n, UInt(OUTPUT_WIDTH.W)))
    val reset = Input(Bool())
  })

  val dataValid = io.in_a.valid && io.in_b.valid

  val busy = RegInit(false.B)

  io.in_a.ready := !busy
  io.in_b.ready := !busy

  val matrixAReg = RegInit(
    VecInit.fill(n)(VecInit.fill(n)(0.U(INPUT_WIDTH.W)))
  )
  val matrixBReg = RegInit(
    VecInit.fill(n)(VecInit.fill(n)(0.U(INPUT_WIDTH.W)))
  )

  val sysmm = Module(new INT4SystolicMMv2(n))
  sysmm.io.reset := false.B
  for (i <- 0 until n) {
    sysmm.io.in_a(i) := 0.U
    sysmm.io.in_b(i) := 0.U
  }

  when(dataValid) {
    for (i <- 0 until n) {
      for (j <- 0 until n) {
        matrixAReg(i)(j) := io.in_a.bits(i * n + j)
        matrixBReg(i)(j) := io.in_b.bits(i * n + j)
      }
    }
    busy := true.B
  }

  val resValid = RegInit(false.B)
  io.out.valid := resValid
  io.out.bits := sysmm.io.out

  val total_cycles = 3 * n + PE_DELAY
  val cnt = Counter(total_cycles)
  when(busy && cnt.value < (2 * n).U) {
    for (i <- 0 until n) {
      val temp = cnt.value >= i.U
      val p = Mux(temp, cnt.value - i.U, 0.U)
      when(temp && p < n.U) {
        sysmm.io.in_a(i) := matrixAReg(i)(p(log2Ceil(n) - 1, 0))
        sysmm.io.in_b(i) := matrixBReg(p(log2Ceil(n) - 1, 0))(i)
      }
      printf(p"in_a${i}: ${sysmm.io.in_a(i)} in_b${i}: ${sysmm.io.in_b(i)}\t")
    }
    cnt.inc()
  }.elsewhen(busy && cnt.value < (3 * n + PE_DELAY - 1).U) {
    cnt.inc()
  }

  when(cnt.value === (3 * n + PE_DELAY - 1).U) {
    resValid := true.B
    // debugLog(p"res: ${sysmm.io.out}\n", LogLevel.DEBUG)
    when(resValid && io.out.ready) {
      resValid := false.B
      busy := false.B
      cnt.reset()
      sysmm.io.reset := io.reset
    }
  }

  // debugLog(p"busy: ${busy} resValid: ${resValid} cnt: ${cnt.value}\n", LogLevel.DEBUG)
}
