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

  val readyReg = RegInit(true.B)
  io.a_in.ready := readyReg
  io.b_in.ready := readyReg

  // // 输出就绪信号
  // val doneReg = RegInit(false.B)
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
  fpmac.io.input := DontCare
  fpmac.io.weight := DontCare
  fpmac.io.psum := DontCare
  fpmac.io.valid_in := false.B

  // 状态转换和数据处理逻辑

  switch(state) {
    is(s_idle) {
      when(io.reset) {
        res := 0.U
        aReg := 0.U
        bReg := 0.U
      }
      io.out.valid := false.B
      fpmac.io.valid_in := false.B

      io.out.valid := false.B
      io.a_out.valid := false.B
      io.b_out.valid := false.B
      // 等待输入数据
      when(io.a_in.valid && io.b_in.valid) {

        readyReg := false.B
        aReg := io.a_in.bits
        bReg := io.b_in.bits
        state := s_computing
      }
    }
    is(s_computing) {
      fpmac.io.input := aReg
      fpmac.io.weight := bReg
      fpmac.io.psum := res
      fpmac.io.valid_in := true.B
      // 执行计算
      when(fpmac.io.valid_out) {
        fpmac.io.valid_in := false.B
        res := fpmac.io.out
        state := s_done

      }
    }
    is(s_done) {
      io.a_out.valid := true.B
      io.b_out.valid := true.B
      io.a_out.bits := aReg
      io.b_out.bits := bReg
      io.out.valid := true.B
      io.out.bits := res

      // 计算完成，等待新的输入
      readyReg := true.B
      state := s_idle
    }

  }

  // 打印调试信息
  printf(
    p"[PEFp] - Input: a=0x${Hexadecimal(aReg)}, b=0x${Hexadecimal(bReg)}\n"
  )
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

  // 创建输入队列 - 每行/列数据单独一个队列
  val aQueues = Seq.fill(n)(Module(new Queue(Vec(n, UInt(TOTAL_WIDTH.W)), 4)))
  val bQueues = Seq.fill(n)(Module(new Queue(Vec(n, UInt(TOTAL_WIDTH.W)), 4)))

  // 连接输入队列
  for (i <- 0 until n) {
    // 每个aQueues(i)存储A矩阵的第i行
    aQueues(i).io.enq.valid := io.a_inputs.valid
    aQueues(i).io.enq.bits := io.a_inputs.bits(i)

    // 每个bQueues(j)存储B矩阵的第j列（通过矩阵转置实现）
    bQueues(i).io.enq.valid := io.b_inputs.valid
    bQueues(i).io.enq.bits := VecInit(
      Seq.tabulate(n)(j => io.b_inputs.bits(j)(i))
    )
  }

  // 只有所有队列都ready时，输入才ready
  io.a_inputs.ready := aQueues.map(_.io.enq.ready).reduce(_ && _)
  io.b_inputs.ready := bQueues.map(_.io.enq.ready).reduce(_ && _)

  // 创建PE阵列
  val pes = Seq.tabulate(n, n)((i, j) => Module(new PEFp(useHalf)))
  val pesIO = Seq.tabulate(n, n)((i, j) => pes(i)(j).io)

  // 状态寄存器
  val s_idle :: s_computing :: s_done :: Nil = Enum(3)
  val state = RegInit(s_idle)
  val current_cycle = RegInit(0.U(log2Ceil(n * n).W))

  // 标记队列的数据消费状态（用于控制波浪式数据传播）
  val aQueueProgress = RegInit(VecInit(Seq.fill(n)(0.U(log2Ceil(n + 1).W))))
  val bQueueProgress = RegInit(VecInit(Seq.fill(n)(0.U(log2Ceil(n + 1).W))))

  // 设置所有PE的reset信号
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      pesIO(i)(j).reset := io.reset
    }
  }

  // 连接PE阵列
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      // 配置数据输入
      if (i == 0 && j == 0) {
        // 左上角PE - 直接连接到队列
        val aDeq = Wire(Decoupled(UInt(TOTAL_WIDTH.W)))
        val bDeq = Wire(Decoupled(UInt(TOTAL_WIDTH.W)))

        // 使用进度计数器控制数据流动
        aDeq.valid := aQueues(i).io.deq.valid && (state === s_computing) &&
          (aQueueProgress(i) === j.U)
        aDeq.bits := aQueues(i).io.deq.bits(j)

        bDeq.valid := bQueues(j).io.deq.valid && (state === s_computing) &&
          (bQueueProgress(j) === i.U)
        bDeq.bits := bQueues(j).io.deq.bits(i)

        pesIO(i)(j).a_in <> aDeq
        pesIO(i)(j).b_in <> bDeq

        // 队列出队控制 - 只有在行/列最后一个元素被消费时才出队
        aQueues(
          i
        ).io.deq.ready := aDeq.ready && aDeq.valid && (j.U === (n - 1).U)
        bQueues(
          j
        ).io.deq.ready := bDeq.ready && bDeq.valid && (i.U === (n - 1).U)
      } else if (i == 0) {
        // 第一行其他PE - 等待前一个PE的a_out才启动
        pesIO(i)(j).a_in <> pesIO(i)(j - 1).a_out

        val bDeq = Wire(Decoupled(UInt(TOTAL_WIDTH.W)))
        bDeq.valid := bQueues(j).io.deq.valid && pesIO(i)(j - 1).a_out.valid &&
          (bQueueProgress(j) === i.U)
        bDeq.bits := bQueues(j).io.deq.bits(i)

        pesIO(i)(j).b_in <> bDeq

        // 队列出队控制 - 只有在列的最后一个元素被消费时才出队
        bQueues(
          j
        ).io.deq.ready := bDeq.ready && bDeq.valid && (i.U === (n - 1).U)
      } else if (j == 0) {
        // 第一列其他PE - 等待上一个PE的b_out才启动
        val aDeq = Wire(Decoupled(UInt(TOTAL_WIDTH.W)))
        aDeq.valid := aQueues(i).io.deq.valid && pesIO(i - 1)(j).b_out.valid &&
          (aQueueProgress(i) === j.U)
        aDeq.bits := aQueues(i).io.deq.bits(j)

        pesIO(i)(j).a_in <> aDeq
        pesIO(i)(j).b_in <> pesIO(i - 1)(j).b_out

        // 队列出队控制 - 只有在行的最后一个元素被消费时才出队
        aQueues(
          i
        ).io.deq.ready := aDeq.ready && aDeq.valid && (j.U === (n - 1).U)
      } else {
        // 其他PE - 依赖于前一行和前一列的输出
        pesIO(i)(j).a_in <> pesIO(i)(j - 1).a_out
        pesIO(i)(j).b_in <> pesIO(i - 1)(j).b_out
      }
    }
  }

  // 收集输出结果
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      io.output_matrix.bits(i)(j) := pesIO(i)(j).out.bits
    }
  }

  // 计算完成信号 - 所有PE都完成计算
  val peValidSignals = VecInit(for {
    i <- 0 until n
    j <- 0 until n
  } yield pesIO(i)(j).out.valid)
  val allPEsDone = peValidSignals.asUInt.andR

  // 队列进度更新逻辑
  when(state === s_computing && allPEsDone) {
    for (i <- 0 until n) {
      when(aQueueProgress(i) < n.U) {
        aQueueProgress(i) := aQueueProgress(i) + 1.U
      }
      when(bQueueProgress(i) < n.U) {
        bQueueProgress(i) := bQueueProgress(i) + 1.U
      }
    }
    current_cycle := current_cycle + 1.U
  }

  // 状态转换逻辑
  when(io.reset) {
    state := s_idle
    current_cycle := 0.U
    for (i <- 0 until n) {
      aQueueProgress(i) := 0.U
      bQueueProgress(i) := 0.U
    }
    io.output_matrix.valid := false.B
  }.otherwise {
    switch(state) {
      is(s_idle) {
        // 等待所有队列都有数据
        when(
          aQueues.map(_.io.deq.valid).reduce(_ && _) &&
            bQueues.map(_.io.deq.valid).reduce(_ && _)
        ) {
          state := s_computing
          current_cycle := 0.U
          for (i <- 0 until n) {
            aQueueProgress(i) := 0.U
            bQueueProgress(i) := 0.U
          }
        }
      }
      is(s_computing) {
        // 检查是否完成所有计算周期
        when(allPEsDone && current_cycle === ((n * n) - 1).U) {
          io.output_matrix.valid := true.B
          state := s_done
        }
      }
      is(s_done) {
        io.output_matrix.valid := false.B
        state := s_idle
      }
    }
  }
}

class OptimizedSystolicMM(val n: Int, val useHalf: Boolean) extends Module {
  val TOTAL_WIDTH = if (useHalf) 16 else 32

  val io = IO(new Bundle {
    val a_inputs =
      Flipped(Decoupled(Vec(n, Vec(n, UInt(TOTAL_WIDTH.W))))) // 按行输入
    val b_inputs =
      Flipped(Decoupled(Vec(n, Vec(n, UInt(TOTAL_WIDTH.W))))) // 按列输入
    val output_matrix = Decoupled(Vec(n, Vec(n, UInt(TOTAL_WIDTH.W))))
    val reset = Input(Bool())
  })

  // ===============================
  // 输入队列重构
  // ===============================
  // A矩阵行队列（每行一个队列）
  val aRowQueues = Seq.tabulate(n)(i =>
    Module(new Queue(UInt(TOTAL_WIDTH.W), entries = 2 * n)) // 深度为2倍行宽
  )

  // B矩阵列队列（每列一个队列）
  val bColQueues = Seq.tabulate(n)(j =>
    Module(new Queue(UInt(TOTAL_WIDTH.W), entries = 2 * n))
  ) // 深度为2倍列高

  // 连接输入到队列 - 按行输入A矩阵，按列输入B矩阵
  for (i <- 0 until n) {
    // 输入A矩阵的第i行
    for (j <- 0 until n) {
      aRowQueues(i).io.enq.bits := io.a_inputs.bits(i)(j)
      printf(p"[OptimizedSystolicMM] - i: ${i}, j: ${j}, aRowQueues(i).io.enq.bits: ${Hexadecimal(aRowQueues(i).io.enq.bits)}\n")
      aRowQueues(i).io.enq.valid := io.a_inputs.valid
    }
    // 输入B矩阵的第i列
    for (j <- 0 until n) {
      bColQueues(i).io.enq.bits := io.b_inputs.bits(j)(i)
      printf(p"[OptimizedSystolicMM] - i: ${i}, j: ${j}, bColQueues(i).io.enq.bits: ${Hexadecimal(bColQueues(i).io.enq.bits)}\n")
      bColQueues(i).io.enq.valid := io.b_inputs.valid
    }
  }
  io.a_inputs.ready := aRowQueues.map(_.io.enq.ready).reduce(_ && _)
  io.b_inputs.ready := bColQueues.map(_.io.enq.ready).reduce(_ && _)

  // ===============================
  // PE阵列重构
  // ===============================
  val peArray = Seq.tabulate(n, n)((i, j) => Module(new PEFp(useHalf)))

  // 设置所有PE的reset信号
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      peArray(i)(j).io.reset := io.reset
      peArray(i)(j).io.a_out.ready := true.B
      peArray(i)(j).io.b_out.ready := true.B
      peArray(i)(j).io.out.ready := true.B
    }
  }

  // ===============================
  // 数据流控制逻辑
  // ===============================
  // 行首PE的A输入控制
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      // 水平方向连接（A数据流）
      if (j == 0) {
        // 行首PE连接行队列
        peArray(i)(j).io.a_in.valid := aRowQueues(i).io.deq.valid
        peArray(i)(j).io.a_in.bits := aRowQueues(i).io.deq.bits
        // 队列出队控制
        if (i == 0) {
          aRowQueues(i).io.deq.ready := peArray(i)(j).io.a_in.ready
        } else {
          aRowQueues(i).io.deq.ready := peArray(i - 1)(j).io.b_out.valid && peArray(i)(j).io.a_in.ready
        }
      } else {
        // 后续PE连接前一个PE的输出
        peArray(i)(j).io.a_in <> peArray(i)(j - 1).io.a_out
      }

      // 垂直方向连接（B数据流）
      if (i == 0) {
        // 列首PE连接列队列
        peArray(i)(j).io.b_in.valid := bColQueues(j).io.deq.valid
        peArray(i)(j).io.b_in.bits := bColQueues(j).io.deq.bits
        // 队列出队控制
        if (j == 0) {
          bColQueues(j).io.deq.ready := peArray(i)(j).io.b_in.ready
        } else {
          bColQueues(j).io.deq.ready := peArray(i)(j - 1).io.a_out.valid && peArray(i)(j).io.b_in.ready
        }
      } else {
        // 后续PE连接上一个PE的输出
        peArray(i)(j).io.b_in <> peArray(i - 1)(j).io.b_out
      }
    }
  }

  // ===============================
  // 输出收集逻辑
  // ===============================
  val outputBuffer = Reg(Vec(n, Vec(n, UInt(TOTAL_WIDTH.W))))
  val outputValidReg = RegInit(false.B)
  val computeCycle = RegInit(0.U(log2Ceil(n * n).W))
  
  // 检测所有PE的输出有效性
  val allPEsValid = VecInit(for {
    i <- 0 until n
    j <- 0 until n
  } yield peArray(i)(j).io.out.valid).asUInt.andR
  
  when(io.reset) {
    outputValidReg := false.B
    computeCycle := 0.U
  }.elsewhen(allPEsValid) {
    computeCycle := computeCycle + 1.U
    when(computeCycle === (n * n - 1).U) {
      outputValidReg := true.B
      for (i <- 0 until n) {
        for (j <- 0 until n) {
          outputBuffer(i)(j) := peArray(i)(j).io.out.bits
        }
      }
    }
  }.elsewhen(io.output_matrix.ready && outputValidReg) {
    outputValidReg := false.B
    computeCycle := 0.U
  }

  io.output_matrix.bits := outputBuffer
  io.output_matrix.valid := outputValidReg

  // ===============================
  // 全局复位逻辑
  // ===============================
  when(io.reset) {
    outputValidReg := false.B
    aRowQueues.foreach(_.reset := true.B)
    bColQueues.foreach(_.reset := true.B)
    peArray.foreach(_.foreach(_.reset := true.B))
  }.otherwise {
    aRowQueues.foreach(_.reset := false.B)
    bColQueues.foreach(_.reset := false.B)
    peArray.foreach(_.foreach(_.reset := false.B))
  }
}
