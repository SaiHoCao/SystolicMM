package fpmac

import chisel3._
import chisel3.util._

// 单个处理单元（PE）的定义
class PEFp(useHalf: Boolean) extends Module {
  val TOTAL_WIDTH = if (useHalf) 16 else 32

  val io = IO(new Bundle {
    // 数据输入接口
    val a_in = Flipped(Decoupled(UInt(TOTAL_WIDTH.W)))
    val b_in = Flipped(Decoupled(UInt(TOTAL_WIDTH.W)))
    // 数据输出接口
    val a_out = Decoupled(UInt(TOTAL_WIDTH.W))
    val b_out = Decoupled(UInt(TOTAL_WIDTH.W))
    val out = Decoupled(UInt(TOTAL_WIDTH.W))
    // 控制信号
    val reset = Input(Bool())
  })

  // 输出就绪信号
  val readyReg = RegInit(true.B)
  io.a_in.ready := readyReg
  io.b_in.ready := readyReg

  io.a_out.bits := DontCare
  io.a_out.valid := false.B
  io.b_out.bits := DontCare
  io.b_out.valid := false.B
  io.out.bits := DontCare
  io.out.valid := false.B

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
  fpmac.io.input := aReg
  fpmac.io.weight := bReg
  fpmac.io.psum := res
  fpmac.io.valid_in := (state === s_computing)

  // 状态转换和数据处理逻辑
  when(io.reset) {
    state := s_idle
    res := 0.U
    aReg := 0.U
    bReg := 0.U
    io.a_out.valid := false.B
    io.b_out.valid := false.B
    io.out.valid := false.B
  }.otherwise {
    switch(state) {
      is(s_idle) {
        io.out.valid := false.B
        io.a_out.valid := false.B
        io.b_out.valid := false.B
        // 等待输入数据
        when(io.a_in.valid && io.b_in.valid) {
          aReg := io.a_in.bits
          bReg := io.b_in.bits
          state := s_computing
        }
      }
      is(s_computing) {
        // 执行计算
        when(fpmac.io.valid_out) {
          res := fpmac.io.out
          // 检查是否完成所有计算
          state := s_done

        }
      }
      is(s_done) {
        io.a_out.valid := true.B
        io.b_out.valid := true.B
        io.a_out.bits := io.a_in.bits
        io.b_out.bits := io.b_in.bits
        io.out.valid := true.B
        io.out.bits := res
        // 计算完成，等待新的输入
        state := s_idle
      }
    }
  }

  // 打印调试信息
  printf(p"[PEFp] - Input: a=0x${Hexadecimal(aReg)}, b=0x${Hexadecimal(bReg)}\n")
  printf(p"[PEFp] - Output: res=0x${Hexadecimal(res)}\n")
}

// 脉动阵列矩阵乘法器
class SystolicMM(val n: Int, val useHalf: Boolean) extends Module {
  val TOTAL_WIDTH = if (useHalf) 16 else 32

  val io = IO(new Bundle {
    val a_inputs = Flipped(Decoupled(Vec(n, Vec(n, UInt(TOTAL_WIDTH.W)))))
    val b_inputs = Flipped(Decoupled(Vec(n, Vec(n, UInt(TOTAL_WIDTH.W)))))
    val output_matrix = Decoupled(Vec(n, Vec(n, UInt(TOTAL_WIDTH.W))))
    val reset = Input(Bool())
  })

  // 创建PE阵列
  val pes = VecInit(
    Seq.tabulate(n)(i =>
      VecInit(Seq.tabulate(n)(j => Module(new PEFp(useHalf)).io))
    )
  )

  // 状态寄存器
  val s_idle :: s_computing :: s_done :: Nil = Enum(3)
  val state = RegInit(s_idle)
  val current_row = RegInit(0.U(log2Ceil(n).W))
  val current_col = RegInit(0.U(log2Ceil(n).W))

  // 连接PE阵列
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      // 基本连接
      pes(i)(j).reset := io.reset

      // 数据输入连接
      if (i == 0 && j == 0) {
        // 左上角PE
        pes(i)(j).a_in.valid := io.a_inputs.valid
        pes(i)(j).b_in.valid := io.b_inputs.valid
        pes(i)(j).a_in.bits := io.a_inputs.bits(current_row)(current_col)
        pes(i)(j).b_in.bits := io.b_inputs.bits(current_row)(current_col)
      } else if (i == 0) {
        // 第一行其他PE
        pes(i)(j).a_in.valid := pes(i)(j - 1).a_out.valid
        pes(i)(j).b_in.valid := io.b_inputs.valid
        pes(i)(j).a_in.bits := pes(i)(j - 1).a_out.bits
        pes(i)(j).b_in.bits := io.b_inputs.bits(current_row)(j)
      } else if (j == 0) {
        // 第一列其他PE
        pes(i)(j).a_in.valid := io.a_inputs.valid
        pes(i)(j).b_in.valid := pes(i - 1)(j).b_out.valid
        pes(i)(j).a_in.bits := io.a_inputs.bits(i)(current_col)
        pes(i)(j).b_in.bits := pes(i - 1)(j).b_out.bits
      } else {
        // 其他PE
        pes(i)(j).a_in.valid := pes(i)(j - 1).a_out.valid
        pes(i)(j).b_in.valid := pes(i - 1)(j).b_out.valid
        pes(i)(j).a_in.bits := pes(i)(j - 1).a_out.bits
        pes(i)(j).b_in.bits := pes(i - 1)(j).b_out.bits
      }

      // 输出连接
      io.output_matrix.bits(i)(j) := pes(i)(j).out.bits
    }
  }

  // 状态转换逻辑
  when(io.reset) {
    state := s_idle
    current_row := 0.U
    current_col := 0.U
    io.output_matrix.valid := false.B
  }.otherwise {
    switch(state) {
      is(s_idle) {
        when(io.a_inputs.valid && io.b_inputs.valid) {
          state := s_computing
        }
      }
      is(s_computing) {
        // 检查所有PE是否完成计算
        when(pes(n - 1)(n - 1).out.valid) {
          io.output_matrix.valid := true.B
          state := s_done
        }
      }
      is(s_done) {
        io.output_matrix.valid := false.B
        // 更新输入位置
        when(current_col === (n - 1).U) {
          current_col := 0.U
          when(current_row === (n - 1).U) {
            current_row := 0.U
            state := s_idle
          }.otherwise {
            current_row := current_row + 1.U
            state := s_idle
          }
        }.otherwise {
          current_col := current_col + 1.U
          state := s_idle
        }
      }
    }
  }

  // 输入就绪信号
  io.a_inputs.ready := (state === s_idle)
  io.b_inputs.ready := (state === s_idle)
}
