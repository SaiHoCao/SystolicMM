package fpmac

import chisel3._
import chisel3.util._

// 单个处理单元（PE）的定义
class PEFpv2(useHalf: Boolean) extends Module {
  val TOTAL_WIDTH = if (useHalf) 16 else 32

  val io = IO(new Bundle {
    // 数据输入接口
    val a_in = Input(UInt(TOTAL_WIDTH.W))
    val b_in = Input(UInt(TOTAL_WIDTH.W))
    // 数据输出接口
    val a_out = Output(UInt(TOTAL_WIDTH.W))
    val b_out = Output(UInt(TOTAL_WIDTH.W))
    val out = Output(UInt(TOTAL_WIDTH.W))
    val valid_out = Output(Bool())
    // 控制信号
    val reset = Input(Bool())
  })

  io.valid_out := false.B
  io.a_out := 0.U
  io.b_out := 0.U
  io.out := 0.U

  // 实例化FPMAC
  val fpmac = Module(new FPMAC_5S(useHalf))
  val res = RegInit(0.U(TOTAL_WIDTH.W))

  // 数据传递寄存器
  val aReg = RegInit(0.U(TOTAL_WIDTH.W))
  val bReg = RegInit(0.U(TOTAL_WIDTH.W))

  // 状态寄存器
  val s_idle :: s_computing :: s_done :: Nil = Enum(3)
  val state = RegInit(s_idle)

  // 连接FPMAC
  fpmac.io.input := DontCare
  fpmac.io.weight := DontCare
  fpmac.io.psum := DontCare
  fpmac.io.valid_in := false.B

  // 状态转换和数据处理逻辑

  switch(state) {
    is(s_idle) {
      when(io.reset) {
        res := 0.U
        state := s_idle
      }
      io.valid_out := false.B
      fpmac.io.valid_in := false.B
      // 等待输入数据
      aReg := io.a_in
      printf(p"aReg: ${aReg}, A_in: ${io.a_in}\n")
      bReg := io.b_in
      printf(p"bReg: ${bReg}, B_in: ${io.b_in}\n")
      state := s_computing
    }
    is(s_computing) {
      fpmac.io.valid_in := true.B
      fpmac.io.input := aReg
      fpmac.io.weight := bReg
      fpmac.io.psum := res
      printf(p"fpmac.io.valid_in: ${fpmac.io.valid_in}\n")
      // 执行计算
      when(fpmac.io.valid_out) {
        fpmac.io.valid_in := false.B
        res := fpmac.io.out
        printf(p"fpmac.io.out: ${fpmac.io.out}\n")
        state := s_done
      }
    }
    is(s_done) {
      io.valid_out := true.B
      io.a_out := aReg
      io.b_out := bReg
      io.out := res
      state := s_idle
    }

  }

  // 打印调试信息
  printf(
    p"[PEFp] - Input: a=0x${Hexadecimal(aReg)}, b=0x${Hexadecimal(bReg)}\n"
  )
  printf(p"[PEFp] - Output: res=0x${Hexadecimal(res)}\n")
}

class SystolicMMv2(val n: Int = 4, useHalf: Boolean = false) extends Module {
  val TOTAL_WIDTH = if (useHalf) 16 else 32
  val io = IO(new Bundle {
    val in_a = Input(Vec(n, UInt(TOTAL_WIDTH.W)))
    val in_b = Input(Vec(n, UInt(TOTAL_WIDTH.W)))
    val out = Output(Vec(n * n, UInt(TOTAL_WIDTH.W)))
    val reset = Input(Bool())
  })

  val peElements = Seq.tabulate(n * n)((i) => Module(new PEFpv2(useHalf)))
  peElements.foreach(_.io.reset := io.reset)

  val h_wires = Wire(Vec((n - 1) * n, UInt(TOTAL_WIDTH.W)))
  val v_wires = Wire(Vec(n * (n - 1), UInt(TOTAL_WIDTH.W)))

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
class GEMM(val n: Int = 4, useHalf: Boolean = false) extends Module {
  val TOTAL_WIDTH = if (useHalf) 16 else 32
  val PE_DELAY = 5
  val io = IO(new Bundle {
    val in_a = Flipped(Decoupled(Vec(n * n, UInt(TOTAL_WIDTH.W))))
    val in_b = Flipped(Decoupled(Vec(n * n, UInt(TOTAL_WIDTH.W))))
    val out = Decoupled(Vec(n * n, UInt(TOTAL_WIDTH.W)))
    val reset = Input(Bool())
  })

  val dataValid = io.in_a.valid && io.in_b.valid

  val busy = RegInit(false.B)

  io.in_a.ready := !busy
  io.in_b.ready := !busy

  val matrixAReg = RegInit(
    VecInit.fill(n)(VecInit.fill(n)(0.U(TOTAL_WIDTH.W)))
  )
  val matrixBReg = RegInit(
    VecInit.fill(n)(VecInit.fill(n)(0.U(TOTAL_WIDTH.W)))
  )

  val sysmm = Module(new SystolicMMv2(n, useHalf))
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
