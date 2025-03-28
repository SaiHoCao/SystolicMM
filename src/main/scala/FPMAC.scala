package fpmac

import chisel3._
import chisel3.util._

class FPMUL(val useHalf: Boolean = false) extends Module {
  // 参数化配置
  val TOTAL_WIDTH = if (useHalf) 16 else 32
  val EXP_WIDTH = if (useHalf) 5 else 8
  val MANT_WIDTH = if (useHalf) 10 else 23
  val BIAS = if (useHalf) 15 else 127

  // 精确计算中间位宽
  val FULL_MANT = MANT_WIDTH + 1 // 含隐含位
  val MUL_WIDTH = 2 * FULL_MANT // 乘法结果位宽
  val FULL_EXP = EXP_WIDTH + 1 // 多一位，用于判断溢出

  val io = IO(new Bundle {
    val inputA = Input(UInt(TOTAL_WIDTH.W))
    val inputB = Input(UInt(TOTAL_WIDTH.W))
    val out = Output(UInt(TOTAL_WIDTH.W))
    val sign_out = Output(Bool())
    val exp_out = Output(UInt(EXP_WIDTH.W))
    val mant_out = Output(UInt(MANT_WIDTH.W))
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())
  })

  // 初始化输出
  io.exp_out := 0.U
  io.mant_out := 0.U
  io.sign_out := false.B
  io.out := 0.U
  io.valid_out := false.B

  // // 分解输入的浮点数
  // def decodeFP(data: UInt) = {
  //   val sign = data(TOTAL_WIDTH - 1)
  //   val exp = data(TOTAL_WIDTH - 2, MANT_WIDTH)
  //   val mant = data(MANT_WIDTH - 1, 0)
  //   val full_mant = Cat(1.U(1.W), mant) // 添加隐含位1
  //   (sign, exp, full_mant)
  // }

// Stage1 逻辑运算，指数，尾数，符号位运算
//   val stage1 = Reg(new Bundle {
//     val res_sign = Bool()
//     val res_mant = UInt(MUL_WIDTH.W)
//     val res_exp = UInt(FULL_EXP.W)
//     val valid = Bool()
//   })
// // stage1 逻辑运算
//   when(io.valid_in) {
//     // 提取输入的符号位、指数和尾数
//     val (a_sign, a_exp, a_mant) = decodeFP(io.inputA)
//     val (b_sign, b_exp, b_mant) = decodeFP(io.inputB)

//     // 检查特殊情况：输入是否为0
//     val a_is_zero = (a_exp === 0.U) && (a_mant(MANT_WIDTH - 1, 0) === 0.U)
//     val b_is_zero = (b_exp === 0.U) && (b_mant(MANT_WIDTH - 1, 0) === 0.U)
//     val is_zero = a_is_zero || b_is_zero

//     when(is_zero) {
//       stage1.res_sign := 0.U
//       stage1.res_exp := 0.U
//       stage1.res_mant := 0.U
//     }.otherwise {
//       // 计算结果的符号位（异或操作）
//       stage1.res_sign := a_sign ^ b_sign
//       stage1.res_exp := (a_exp +& b_exp -& BIAS.U).asUInt
//       stage1.res_mant := a_mant * b_mant
//     }
//     stage1.valid := true.B
//   }

// // stage2 归一化和舍入
//   val stage2 = Reg(new Bundle {
//     val out_sign = Bool()
//     val out_mant = UInt(MANT_WIDTH.W)
//     val out_exp = UInt(EXP_WIDTH.W)
//     val valid = Bool()
//   })

//   when(stage1.valid) {
//     // 检查结果是否为0
//     val out_zero = (stage1.res_mant === 0.U) && (stage1.res_exp === 0.U)

//     when(out_zero) {
//       stage2.out_sign := 0.U // 零的符号位总是正
//       stage2.out_mant := 0.U
//       stage2.out_exp := 0.U
//     }.otherwise {
//       val normalized_mant = Wire(UInt(MUL_WIDTH.W))
//       val normalized_exp = Wire(UInt(FULL_EXP.W))
//       val res_mant = stage1.res_mant
//       val res_exp = stage1.res_exp
//       when(res_mant(MUL_WIDTH - 1) === 1.U) {
//         normalized_mant := res_mant << 1
//         normalized_exp := res_exp + 1.U
//       }.elsewhen(res_mant(MUL_WIDTH - 2) === 1.U) {
//         normalized_mant := res_mant << 2
//         normalized_exp := res_exp
//       }.elsewhen(res_mant(MUL_WIDTH - 3) === 1.U) {
//         normalized_mant := res_mant << 3
//         normalized_exp := res_exp - 1.U
//       }.elsewhen(res_mant(MUL_WIDTH - 4) === 1.U) {
//         normalized_mant := res_mant << 4
//         normalized_exp := res_exp - 2.U
//       }.elsewhen(res_mant(MUL_WIDTH - 5) === 1.U) {
//         normalized_mant := res_mant << 5
//         normalized_exp := res_exp - 3.U
//       }.elsewhen(res_mant(MUL_WIDTH - 6) === 1.U) {
//         normalized_mant := res_mant << 6
//         normalized_exp := res_exp - 4.U
//       }.elsewhen(res_mant(MUL_WIDTH - 7) === 1.U) {
//         normalized_mant := res_mant << 7
//         normalized_exp := res_exp - 5.U
//       }.elsewhen(res_mant(MUL_WIDTH - 8) === 1.U) {
//         normalized_mant := res_mant << 8
//         normalized_exp := res_exp - 6.U
//       }.elsewhen(res_mant(MUL_WIDTH - 9) === 1.U) {
//         normalized_mant := res_mant << 9
//         normalized_exp := res_exp - 7.U
//       }.elsewhen(res_mant(MUL_WIDTH - 10) === 0.U) {
//         normalized_mant := res_mant << 10
//         normalized_exp := res_exp - 8.U
//       }.otherwise {
//         normalized_mant := res_mant
//         normalized_exp := res_exp
//       }
//       stage2.out_mant := normalized_mant(MUL_WIDTH - 1, MUL_WIDTH - MANT_WIDTH)
//       when(normalized_exp(FULL_EXP - 1) === 1.U) {
//         // 指数下溢时，保留符号位
//         stage2.out_sign := stage1.res_sign
//         stage2.out_mant := 0.U
//         stage2.out_exp := 0.U
//         printf(p"FPMUL: 指数下溢，sign_out=${stage1.res_sign}\n")
//       }.otherwise {
//         stage2.out_sign := stage1.res_sign
//         stage2.out_exp := normalized_exp(FULL_EXP - 2, 0)
//         stage2.out_mant := normalized_mant(
//           MUL_WIDTH - 1,
//           MUL_WIDTH - MANT_WIDTH
//         )
//       }
//     }
//     printf(p"[Stage2-输出] 乘法结果符号=${stage2.out_sign}, 乘法结果指数=${stage2.out_exp}, 乘法结果尾数=${stage2.out_mant}\n")
//     // printf(p"[Stage2-输出] 完整乘法结果=${stage2.mul_out}\n")
//     stage2.valid := true.B
//   }
//   // 修复输出组装，确保符号位正确包含
//   when(stage2.valid) {
//     io.sign_out := stage2.out_sign
//     io.exp_out := stage2.out_exp
//     io.mant_out := stage2.out_mant
//     io.out := Cat(stage2.out_sign, stage2.out_exp, stage2.out_mant)
//     io.valid_out := stage2.valid
//   }

  // 分解输入的浮点数
  def decodeFP(data: UInt) = {
    val sign = data(TOTAL_WIDTH - 1)
    val exp = data(TOTAL_WIDTH - 2, MANT_WIDTH)
    val mant = data(MANT_WIDTH - 1, 0)
    val full_mant = Cat(1.U(1.W), mant) // 添加隐含位
    (sign, exp, full_mant)
  }

  // 五段流水线寄存器定义
  // Stage1: 乘法分解计算
  val stage1 = Reg(new Bundle {
    val mul_sign = Bool()
    val mul_exp = UInt(FULL_EXP.W)
    val mul_mant = UInt(MUL_WIDTH.W)
    val valid = Bool()
  })

  when(io.valid_in) {
    val (a_sign, a_exp, a_mant) = decodeFP(io.inputA)
    val (b_sign, b_exp, b_mant) = decodeFP(io.inputB)

    // 检查特殊情况：输入是否为0
    val a_is_zero = (a_exp === 0.U) && (a_mant(MANT_WIDTH - 1, 0) === 0.U)
    val b_is_zero = (b_exp === 0.U) && (b_mant(MANT_WIDTH - 1, 0) === 0.U)
    val mul_zero = a_is_zero || b_is_zero

    val res_sign = a_sign ^ b_sign
    val res_exp = Mux(mul_zero, 0.U, a_exp +& b_exp - BIAS.U)
    val res_mant = Mux(mul_zero, 0.U, a_mant * b_mant)

    // 添加调试打印 - Stage1输入
    printf(
      p"[Stage1-输入] input=${io.inputA}, weight=${io.inputB}\n"
    )
    printf(p"[Stage1-解析] input: 符号=${a_sign}, 指数=${a_exp}, 尾数=${a_mant}\n")
    printf(p"[Stage1-解析] weight: 符号=${b_sign}, 指数=${b_exp}, 尾数=${b_mant}\n")

    // 添加调试打印 - Stage1计算
    printf(
      p"[Stage1-计算] 输入为0=${a_is_zero}, 权重为0=${b_is_zero}, 乘法为0=${mul_zero}\n"
    )
    printf(p"[Stage1-计算] 结果符号=${res_sign}, 结果指数=${res_exp}, 结果尾数=${res_mant}\n")

    stage1.mul_sign := res_sign
    stage1.mul_exp := res_exp
    stage1.mul_mant := res_mant

    stage1.valid := true.B

  }.otherwise {
    stage1.valid := false.B
  }

  // Stage2: 乘法归一化
  val stage2 = Reg(new Bundle {
    val mul_sign = Bool()
    val mul_exp = UInt(EXP_WIDTH.W)
    val mul_mant = UInt(MANT_WIDTH.W)
    val mul_out = UInt(TOTAL_WIDTH.W)
    val psum = UInt(TOTAL_WIDTH.W)
    val valid = Bool()
  })

  when(stage1.valid) {
    val normalized_exp = Wire(UInt(FULL_EXP.W))
    val normalized_mant = Wire(UInt(MUL_WIDTH.W))

    // 添加调试打印 - Stage2输入
    printf(
      p"[Stage2-输入] 乘法符号=${stage1.mul_sign}, 乘法指数=${stage1.mul_exp}, 乘法尾数=${stage1.mul_mant}\n"
    )

    when((stage1.mul_mant === 0.U) && (stage1.mul_exp === 0.U)) { // 处理零
      normalized_exp := 0.U
      normalized_mant := 0.U
      printf(p"[Stage2-归一化] 尾数为0，结果为0\n")
      // stage2.mul_sign := 0.U // 零的符号位总是正
      // stage2.mul_mant := 0.U
      // stage2.mul_exp := 0.U
    }.otherwise {
      // val normalized_exp = Wire(UInt(FULL_EXP.W))
      // val normalized_mant = Wire(UInt(MUL_WIDTH.W))
      val res_mant = stage1.mul_mant
      val res_exp = stage1.mul_exp
      when(res_mant(MUL_WIDTH - 1) === 1.U) {
        normalized_mant := res_mant << 1
        normalized_exp := res_exp + 1.U
        printf(p"[Stage2-归一化] 左移1位，指数+1=${res_exp + 1.U}\n")
      }.elsewhen(res_mant(MUL_WIDTH - 2) === 1.U) {
        normalized_mant := res_mant << 2
        normalized_exp := res_exp
        printf(p"[Stage2-归一化] 左移2位，指数不变=${res_exp}\n")
      }.elsewhen(res_mant(MUL_WIDTH - 3) === 1.U) {
        normalized_mant := res_mant << 3
        normalized_exp := res_exp - 1.U
        printf(p"[Stage2-归一化] 左移3位，指数-1=${res_exp - 1.U}\n")
      }.elsewhen(res_mant(MUL_WIDTH - 4) === 1.U) {
        normalized_mant := res_mant << 4
        normalized_exp := res_exp - 2.U
        printf(p"[Stage2-归一化] 左移4位，指数-2=${res_exp - 2.U}\n")
      }.elsewhen(res_mant(MUL_WIDTH - 5) === 1.U) {
        normalized_mant := res_mant << 5
        normalized_exp := res_exp - 3.U
        printf(p"[Stage2-归一化] 左移5位，指数-3=${res_exp - 3.U}\n")
      }.elsewhen(res_mant(MUL_WIDTH - 6) === 1.U) {
        normalized_mant := res_mant << 6
        normalized_exp := res_exp - 4.U
        printf(p"[Stage2-归一化] 左移6位，指数-4=${res_exp - 4.U}\n")
      }.elsewhen(res_mant(MUL_WIDTH - 7) === 1.U) {
        normalized_mant := res_mant << 7
        normalized_exp := res_exp - 5.U
        printf(p"[Stage2-归一化] 左移7位，指数-5=${res_exp - 5.U}\n")
      }.elsewhen(res_mant(MUL_WIDTH - 8) === 1.U) {
        normalized_mant := res_mant << 8
        normalized_exp := res_exp - 6.U
        printf(p"[Stage2-归一化] 左移8位，指数-6=${res_exp - 6.U}\n")
      }.elsewhen(res_mant(MUL_WIDTH - 9) === 1.U) {
        normalized_mant := res_mant << 9
        normalized_exp := res_exp - 7.U
        printf(p"[Stage2-归一化] 左移9位，指数-7=${res_exp - 7.U}\n")
      }.elsewhen(res_mant(MUL_WIDTH - 10) === 0.U) {
        normalized_mant := res_mant << 10
        normalized_exp := res_exp - 8.U
        printf(p"[Stage2-归一化] 左移10位，指数-8=${res_exp - 8.U}\n")
      }.otherwise {
        normalized_mant := res_mant
        normalized_exp := res_exp
        printf(p"[Stage2-归一化] 无需左移，指数不变=${res_exp}\n")
      }
    }

    stage2.mul_mant := normalized_mant(MUL_WIDTH - 1, MUL_WIDTH - MANT_WIDTH)
    stage2.mul_sign := stage1.mul_sign
    stage2.mul_exp := normalized_exp(EXP_WIDTH - 1, 0)
    stage2.mul_out := Cat(
      stage1.mul_sign,
      normalized_exp(EXP_WIDTH - 1, 0),
      normalized_mant(MUL_WIDTH - 1, MUL_WIDTH - MANT_WIDTH)
    )

    printf(
      p"[Stage2-输出] 乘法结果符号=${stage2.mul_sign}, 乘法结果指数=${stage2.mul_exp}, 乘法结果尾数=${stage2.mul_mant}\n"
    )
    printf(p"[Stage2-输出] 完整乘法结果=${stage2.mul_out}\n")

    stage2.valid := true.B
  }.otherwise {
    stage2.valid := false.B
  }

  // 输出组装
  when(stage2.valid) {
    io.sign_out := stage2.mul_sign
    io.exp_out := stage2.mul_exp
    io.mant_out := stage2.mul_mant
    io.out := stage2.mul_out
    io.valid_out := stage2.valid
  }
}

class FPADD(val useHalf: Boolean = false) extends Module {
  // 参数化配置
  val TOTAL_WIDTH = if (useHalf) 16 else 32
  val EXP_WIDTH = if (useHalf) 5 else 8
  val MANT_WIDTH = if (useHalf) 10 else 23
  val BIAS = if (useHalf) 15 else 127

  val FULL_EXP = EXP_WIDTH + 1 // 多一位，用于判断溢出

  val io = IO(new Bundle {
    val floatA = Input(UInt(TOTAL_WIDTH.W))
    val floatB = Input(UInt(TOTAL_WIDTH.W))
    val sum = Output(UInt(TOTAL_WIDTH.W))
    // 调试输出
    val sign_out = Output(Bool())
    val exp_out = Output(UInt(EXP_WIDTH.W))
    val mant_out = Output(UInt(MANT_WIDTH.W))
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())
  })

  // 初始化输出
  io.sign_out := false.B
  io.exp_out := 0.U
  io.mant_out := 0.U
  io.sum := 0.U
  io.valid_out := false.B

  // 分解输入的浮点数
  val a_sign = io.floatA(TOTAL_WIDTH - 1)
  val a_exp = io.floatA(TOTAL_WIDTH - 2, MANT_WIDTH)
  val a_mant = io.floatA(MANT_WIDTH - 1, 0)
  val a_fraction_init = Cat(1.U(1.W), a_mant) // 添加隐含位1

  val b_sign = io.floatB(TOTAL_WIDTH - 1)
  val b_exp = io.floatB(TOTAL_WIDTH - 2, MANT_WIDTH)
  val b_mant = io.floatB(MANT_WIDTH - 1, 0)
  val b_fraction_init = Cat(1.U(1.W), b_mant) // 添加隐含位1

  // 添加调试打印 - 输入
  when(io.valid_in) {
    printf(p"[FPADD-输入] A: 符号=${a_sign}, 指数=${a_exp}, 尾数=${a_mant}\n")
    printf(p"[FPADD-输入] B: 符号=${b_sign}, 指数=${b_exp}, 尾数=${b_mant}\n")
  }

  // 特殊情况检测
  val a_is_zero = (a_exp === 0.U) && (a_mant === 0.U)
  val b_is_zero = (b_exp === 0.U) && (b_mant === 0.U)
  val same_exp_diff_sign =
    (a_exp === b_exp) && (a_mant === b_mant) && (a_sign =/= b_sign)

  val stage1 = Reg(new Bundle {
    val a_is_zero = Bool()
    val b_is_zero = Bool()
    val same_exp_diff_sign = Bool()
    val floatA = UInt(TOTAL_WIDTH.W)
    val floatB = UInt(TOTAL_WIDTH.W)
    val fractionA = UInt((MANT_WIDTH + 1).W)
    val fractionB = UInt((MANT_WIDTH + 1).W)
    val exponent = UInt((EXP_WIDTH + 1).W)
    val valid = Bool()
  })

  // 第一阶段 - 对齐操作
  when(io.valid_in) {
    stage1.a_is_zero := a_is_zero
    stage1.b_is_zero := b_is_zero
    stage1.same_exp_diff_sign := same_exp_diff_sign
    stage1.floatA := io.floatA
    stage1.floatB := io.floatB

    when(a_is_zero || b_is_zero || same_exp_diff_sign) {
      // 特殊情况在下一阶段处理
    }.otherwise {
      // 正常计算流程 - 第一阶段：对齐
      val fractionA_temp = a_fraction_init
      val fractionB_temp = b_fraction_init

      when(b_exp > a_exp) {
        // B的指数更大，移动A的尾数
        val shiftAmount = b_exp - a_exp
        stage1.fractionA := fractionA_temp >> shiftAmount
        stage1.fractionB := fractionB_temp
        stage1.exponent := b_exp
        when(io.valid_in) {
          printf(p"[FPADD-对齐] B指数更大，移动A尾数，移位量=${shiftAmount}\n")
          printf(
            p"[FPADD-对齐] 对齐后A尾数=${fractionA_temp >> shiftAmount}, B尾数=${fractionB_temp}\n"
          )
        }
      }.elsewhen(a_exp > b_exp) {
        // A的指数更大，移动B的尾数
        val shiftAmount = a_exp - b_exp
        stage1.fractionA := fractionA_temp
        stage1.fractionB := fractionB_temp >> shiftAmount
        stage1.exponent := a_exp
        when(io.valid_in) {
          printf(p"[FPADD-对齐] A指数更大，移动B尾数，移位量=${shiftAmount}\n")
          printf(
            p"[FPADD-对齐] 对齐后A尾数=${fractionA_temp}, B尾数=${fractionB_temp >> shiftAmount}\n"
          )
        }
      }.otherwise {
        // 指数相等，无需移位
        stage1.fractionA := fractionA_temp
        stage1.fractionB := fractionB_temp
        stage1.exponent := a_exp
        when(io.valid_in) {
          printf(p"[FPADD-对齐] 指数相等，无需对齐\n")
        }
      }
    }
    stage1.valid := true.B
  }

  val stage2 = Reg(new Bundle {
    val a_is_zero = Bool()
    val b_is_zero = Bool()
    val same_exp_diff_sign = Bool()
    val floatA = UInt(TOTAL_WIDTH.W)
    val floatB = UInt(TOTAL_WIDTH.W)
    val fraction = UInt((MANT_WIDTH + 1).W)
    val exponent = UInt((EXP_WIDTH + 1).W)
    val sign = Bool()
    val mantissa = UInt(MANT_WIDTH.W)
    val valid = Bool()
  })
  // 第二阶段 - 加减运算和归一化
  when(stage1.valid) {
    stage2.a_is_zero := stage1.a_is_zero
    stage2.b_is_zero := stage1.b_is_zero
    stage2.same_exp_diff_sign := stage1.same_exp_diff_sign
    stage2.floatA := stage1.floatA
    stage2.floatB := stage1.floatB

    when(stage1.a_is_zero || stage1.b_is_zero || stage1.same_exp_diff_sign) {
      // 特殊情况在下一阶段处理
    }.otherwise {
      // 加减运算
      val a_sign = stage1.floatA(TOTAL_WIDTH - 1)
      val b_sign = stage1.floatB(TOTAL_WIDTH - 1)

      when(a_sign === b_sign) {
        // 同号相加
        val sum_fraction =
          Cat(0.U(1.W), stage1.fractionA) + Cat(0.U(1.W), stage1.fractionB)
        val cout = sum_fraction(MANT_WIDTH + 1)

        // 如果有进位，需要右移一位并增加指数
        when(cout) {
          stage2.fraction := sum_fraction(MANT_WIDTH + 1, 1)
          stage2.exponent := stage1.exponent + 1.U

          printf(p"[FPADD-计算] 同号相加有进位，右移一位，新指数=${stage1.exponent + 1.U}\n")

        }.otherwise {
          stage2.fraction := sum_fraction(MANT_WIDTH, 0)
          stage2.exponent := stage1.exponent

          printf(p"[FPADD-计算] 同号相加无进位\n")

        }

        stage2.sign := a_sign

        printf(
          p"[FPADD-计算] 同号相加，结果符号=${a_sign}, 结果尾数=${sum_fraction(MANT_WIDTH, 0)}\n"
        )

      }.otherwise {
        // 异号相减
        val sub_a = Cat(0.U(1.W), stage1.fractionA)
        val sub_b = Cat(0.U(1.W), stage1.fractionB)
        val sub_result = Wire(UInt((MANT_WIDTH + 2).W))
        val cout = Wire(Bool())

        when(a_sign) {
          // A为负数，计算B-A
          sub_result := sub_b - sub_a
          cout := sub_result(MANT_WIDTH + 1) // 借位标志

          printf(p"[FPADD-计算] A为负数，B-A，借位=${cout}\n")

        }.otherwise {
          // A为正数，计算A-B
          sub_result := sub_a - sub_b
          cout := sub_result(MANT_WIDTH + 1) // 借位标志

          printf(p"[FPADD-计算] A为正数，A-B，借位=${cout}\n")

        }

        // 确定结果符号
        stage2.sign := cout

        // 如果有借位，需要取补码
        val abs_result = Wire(UInt((MANT_WIDTH + 1).W))
        when(cout) {
          abs_result := (~sub_result(MANT_WIDTH, 0)) + 1.U

          printf(p"[FPADD-计算] 有借位，取补码，结果尾数=${abs_result}\n")

        }.otherwise {
          abs_result := sub_result(MANT_WIDTH, 0)

          printf(p"[FPADD-计算] 无借位，结果尾数=${abs_result}\n")

        }

        // 归一化处理 - 逐位检查
        when(abs_result(MANT_WIDTH) === 0.U) {
          // 需要左移归一化
          when(abs_result(MANT_WIDTH - 1) === 1.U) {
            stage2.fraction := abs_result << 1
            stage2.exponent := stage1.exponent - 1.U

            printf(p"[FPADD-归一化] 左移1位，新指数=${stage1.exponent - 1.U}\n")

          }.elsewhen(abs_result(MANT_WIDTH - 2) === 1.U) {
            stage2.fraction := abs_result << 2
            stage2.exponent := stage1.exponent - 2.U

            printf(p"[FPADD-归一化] 左移2位，新指数=${stage1.exponent - 2.U}\n")

          }.elsewhen(abs_result(MANT_WIDTH - 3) === 1.U) {
            stage2.fraction := abs_result << 3
            stage2.exponent := stage1.exponent - 3.U

            printf(p"[FPADD-归一化] 左移3位，新指数=${stage1.exponent - 3.U}\n")

          }.elsewhen(abs_result(MANT_WIDTH - 4) === 1.U) {
            stage2.fraction := abs_result << 4
            stage2.exponent := stage1.exponent - 4.U

            printf(p"[FPADD-归一化] 左移4位，新指数=${stage1.exponent - 4.U}\n")

          }.elsewhen(abs_result(MANT_WIDTH - 5) === 1.U) {
            stage2.fraction := abs_result << 5
            stage2.exponent := stage1.exponent - 5.U

            printf(p"[FPADD-归一化] 左移5位，新指数=${stage1.exponent - 5.U}\n")

          }.elsewhen(abs_result(MANT_WIDTH - 6) === 1.U) {
            stage2.fraction := abs_result << 6
            stage2.exponent := stage1.exponent - 6.U

            printf(p"[FPADD-归一化] 左移6位，新指数=${stage1.exponent - 6.U}\n")

          }.elsewhen(abs_result(MANT_WIDTH - 7) === 1.U) {
            stage2.fraction := abs_result << 7
            stage2.exponent := stage1.exponent - 7.U

            printf(p"[FPADD-归一化] 左移7位，新指数=${stage1.exponent - 7.U}\n")

          }.elsewhen(abs_result(MANT_WIDTH - 8) === 1.U) {
            stage2.fraction := abs_result << 8
            stage2.exponent := stage1.exponent - 8.U

            printf(p"[FPADD-归一化] 左移8位，新指数=${stage1.exponent - 8.U}\n")

          }.elsewhen(abs_result(MANT_WIDTH - 9) === 1.U) {
            stage2.fraction := abs_result << 9
            stage2.exponent := stage1.exponent - 9.U

            printf(p"[FPADD-归一化] 左移9位，新指数=${stage1.exponent - 9.U}\n")

          }.elsewhen(abs_result(0) === 1.U) {
            stage2.fraction := abs_result << 10
            stage2.exponent := stage1.exponent - 10.U

            printf(p"[FPADD-归一化] 左移10位，新指数=${stage1.exponent - 10.U}\n")

          }.otherwise {
            // 结果为零
            stage2.fraction := 0.U
            stage2.exponent := 0.U
            stage2.sign := false.B

            printf(p"[FPADD-归一化] 结果为零\n")

          }
        }.otherwise {
          // 已经归一化
          stage2.fraction := abs_result
          stage2.exponent := stage1.exponent

          printf(p"[FPADD-归一化] 无需归一化\n")

        }

      }

      // 提取尾数（去掉隐含位）
      stage2.mantissa := stage2.fraction(MANT_WIDTH - 1, 0)

      // 调试打印

      printf(
        p"[FPADD-计算结果] 符号=${stage2.sign}, 指数=${stage2.exponent}, 尾数=${stage2.mantissa}, 完整尾数=${stage2.fraction}\n"
      )

    }
    stage2.valid := true.B
  }

  val stage3 = Reg(new Bundle {
    val result = UInt(TOTAL_WIDTH.W)
    val sign_out = Bool()
    val exp_out = UInt(EXP_WIDTH.W)
    val mant_out = UInt(MANT_WIDTH.W)
    val valid = Bool()
  })

  // 第三阶段 - 最终结果组装
  when(stage2.valid) {
    when(stage2.a_is_zero) {
      // A为零，结果为B
      stage3.result := stage2.floatB
      stage3.sign_out := stage2.floatB(TOTAL_WIDTH - 1)
      stage3.exp_out := stage2.floatB(TOTAL_WIDTH - 2, MANT_WIDTH)
      stage3.mant_out := stage2.floatB(MANT_WIDTH - 1, 0)

      printf(p"[FPADD-特殊情况] A为零，结果为B: ${stage2.floatB}\n")

    }.elsewhen(stage2.b_is_zero) {
      // B为零，结果为A
      stage3.result := stage2.floatA
      stage3.sign_out := stage2.floatA(TOTAL_WIDTH - 1)
      stage3.exp_out := stage2.floatA(TOTAL_WIDTH - 2, MANT_WIDTH)
      stage3.mant_out := stage2.floatA(MANT_WIDTH - 1, 0)

      printf(p"[FPADD-特殊情况] B为零，结果为A: ${stage2.floatA}\n")

    }.elsewhen(stage2.same_exp_diff_sign) {
      // 相同指数不同符号，结果为零
      stage3.result := 0.U
      stage3.sign_out := false.B
      stage3.exp_out := 0.U
      stage3.mant_out := 0.U

      printf(p"[FPADD-特殊情况] 相同指数不同符号，结果为零\n")

    }.otherwise {
      // 检查指数是否为零或下溢
      when(stage2.exponent(EXP_WIDTH)) {
        // 指数下溢，结果为0
        stage3.result := 0.U
        stage3.sign_out := false.B
        stage3.exp_out := 0.U
        stage3.mant_out := 0.U

        printf(p"[FPADD-输出] 指数下溢，结果为零\n")

      }.otherwise {
        // 正常情况，组装结果
        val final_mantissa = stage2.fraction(MANT_WIDTH - 1, 0)
        stage3.result := Cat(
          stage2.sign,
          stage2.exponent(EXP_WIDTH - 1, 0),
          final_mantissa
        )
        stage3.sign_out := stage2.sign
        stage3.exp_out := stage2.exponent(EXP_WIDTH - 1, 0)
        stage3.mant_out := final_mantissa
        printf(
          p"[FPADD-输出] 最终结果=${Cat(stage2.sign, stage2.exponent(EXP_WIDTH - 1, 0), final_mantissa)}, 符号=${stage2.sign}, 指数=${stage2
              .exponent(EXP_WIDTH - 1, 0)}, 尾数=${final_mantissa}\n"
        )

      }
    }

    stage3.valid := true.B
  }
// 输出连接
  when(stage3.valid) {
    io.sum := stage3.result
    io.sign_out := stage3.sign_out
    io.exp_out := stage3.exp_out
    io.mant_out := stage3.mant_out
    io.valid_out := stage3.valid
  }

}

// 用FPMUL 和 FPADD 实现的浮点乘加器
class FPMAC(val useHalf: Boolean = false) extends Module {
  // 参数化配置
  val TOTAL_WIDTH = if (useHalf) 16 else 32
  val EXP_WIDTH = if (useHalf) 5 else 8
  val MANT_WIDTH = if (useHalf) 10 else 23
  val BIAS = if (useHalf) 15 else 127

  // 精确计算中间位宽
  val FULL_MANT = MANT_WIDTH + 1 // 含隐含位
  val MUL_WIDTH = 2 * FULL_MANT // 乘法结果位宽
  val FULL_EXP = EXP_WIDTH + 1 // 多一位，用于判断指数溢出

  val io = IO(new Bundle {
    val input = Input(UInt(TOTAL_WIDTH.W))
    val weight = Input(UInt(TOTAL_WIDTH.W))
    val psum = Input(UInt(TOTAL_WIDTH.W))
    val out = Output(UInt(TOTAL_WIDTH.W))
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())
  })

  io.out := 0.U
  io.valid_out := false.B

  // 浮点乘法器实例化
  val fpmul = Module(new FPMUL(useHalf))
  fpmul.io.inputA := io.input
  fpmul.io.inputB := io.weight
  fpmul.io.valid_in := io.valid_in

  // psum延迟流水线（2级延迟匹配乘法器延迟）
  val psum_pipe = Reg(Vec(2, UInt(TOTAL_WIDTH.W)))
  when(io.valid_in) {
    psum_pipe(0) := io.psum
  }
  for (i <- 1 until 2) {
    psum_pipe(i) := psum_pipe(i - 1)
  }

  // 浮点加法器实例化
  val fpadd = Module(new FPADD(useHalf))
  fpadd.io.floatA := fpmul.io.out
  fpadd.io.floatB := psum_pipe(1) // 使用延迟后的psum

  // valid信号流水线处理
  val valid_pipe = RegInit(VecInit(Seq.fill(2)(false.B)))
  valid_pipe(0) := io.valid_in
  for (i <- 1 until 2) {
    valid_pipe(i) := valid_pipe(i - 1)
  }
  fpadd.io.valid_in := valid_pipe(1) // 延迟2拍后的valid信号

  // 输出连接
  io.out := fpadd.io.sum
  io.valid_out := fpadd.io.valid_out

  // 调试信号连接
  printf(p"[FPMAC] MulResult: ${fpmul.io.out} AddResult: ${fpadd.io.sum}\n")
}

//5段流水线的浮点乘加器
class FPMAC_5S(val useHalf: Boolean = false) extends Module {
  // 参数化配置
  val TOTAL_WIDTH = if (useHalf) 16 else 32
  val EXP_WIDTH = if (useHalf) 5 else 8
  val MANT_WIDTH = if (useHalf) 10 else 23
  val BIAS = if (useHalf) 15 else 127

  // 中间位宽定义
  val FULL_MANT = MANT_WIDTH + 1 // 含隐含位
  val MUL_WIDTH = 2 * FULL_MANT // 乘法结果位宽
  val FULL_EXP = EXP_WIDTH + 1 // 用于指数溢出判断

  val io = IO(new Bundle {
    val input = Input(UInt(TOTAL_WIDTH.W))
    val weight = Input(UInt(TOTAL_WIDTH.W))
    val psum = Input(UInt(TOTAL_WIDTH.W))
    val out = Output(UInt(TOTAL_WIDTH.W))
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())
  })
  // 初始化输出
  io.out := 0.U
  io.valid_out := false.B

  // 分解输入的浮点数
  def decodeFP(data: UInt) = {
    val sign = data(TOTAL_WIDTH - 1)
    val exp = data(TOTAL_WIDTH - 2, MANT_WIDTH)
    val mant = data(MANT_WIDTH - 1, 0)
    val full_mant = Cat(1.U(1.W), mant) // 添加隐含位
    (sign, exp, full_mant)
  }

  // 五段流水线寄存器定义
  // Stage1: 乘法分解计算
  val stage1 = Reg(new Bundle {
    val mul_sign = Bool()
    val mul_exp = UInt(FULL_EXP.W)
    val mul_mant = UInt(MUL_WIDTH.W)
    val psum = UInt(TOTAL_WIDTH.W)
    val valid = Bool()
  })

  when(io.valid_in) {
    val (a_sign, a_exp, a_mant) = decodeFP(io.input)
    val (b_sign, b_exp, b_mant) = decodeFP(io.weight)

    // 检查特殊情况：输入是否为0
    val a_is_zero = (a_exp === 0.U) && (a_mant(MANT_WIDTH - 1, 0) === 0.U)
    val b_is_zero = (b_exp === 0.U) && (b_mant(MANT_WIDTH - 1, 0) === 0.U)
    val mul_zero = a_is_zero || b_is_zero

    val res_sign = a_sign ^ b_sign
    val res_exp = Mux(mul_zero, 0.U, a_exp +& b_exp - BIAS.U)
    val res_mant = Mux(mul_zero, 0.U, a_mant * b_mant)

    // 添加调试打印 - Stage1输入
    printf(
      p"[Stage1-Input] input=${io.input}, weight=${io.weight}, psum=${io.psum}\n"
    )
    printf(
      p"[Stage1-Decode] input: sign=${a_sign}, exponent=${a_exp}, mantissa=${a_mant}\n"
    )
    printf(
      p"[Stage1-Decode] weight: sign=${b_sign}, exponent=${b_exp}, mantissa=${b_mant}\n"
    )

    // 添加调试打印 - Stage1计算
    printf(
      p"[Stage1-Compute] input_is_zero=${a_is_zero}, weight_is_zero=${b_is_zero}, mul_is_zero=${mul_zero}\n"
    )
    printf(
      p"[Stage1-Compute] result_sign=${res_sign}, result_exponent=${res_exp}, result_mantissa=${res_mant}\n"
    )

    stage1.mul_sign := res_sign
    stage1.mul_exp := res_exp
    stage1.mul_mant := res_mant
    stage1.psum := io.psum

    stage1.valid := true.B

  }.otherwise {
    stage1.valid := false.B
  }

  // Stage2: 乘法归一化输出
  val stage2 = Reg(new Bundle {
    val mul_out = UInt(TOTAL_WIDTH.W)
    val psum = UInt(TOTAL_WIDTH.W)
    val valid = Bool()
  })

  when(stage1.valid) {
    val normalized_exp = Wire(UInt(FULL_EXP.W))
    val normalized_mant = Wire(UInt(MUL_WIDTH.W))

    // 添加调试打印 - Stage2输入
    printf(
      p"[Stage2-Input] mul_sign=${stage1.mul_sign}, mul_exponent=${stage1.mul_exp}, mul_mantissa=${stage1.mul_mant}\n"
    )

    when(stage1.mul_mant === 0.U) { // 处理零
      normalized_exp := 0.U
      normalized_mant := 0.U
      printf(p"[Stage2-Normalization] mantissa is zero, result is zero\n")
    }.otherwise {
// val res_mant = stage1.mul_mant
      // val res_exp = stage1.mul_exp
      // when(res_mant(MUL_WIDTH - 1) === 1.U) {
      //   normalized_mant := res_mant << 1
      //   normalized_exp := res_exp + 1.U
      //   printf(p"[Stage2-归一化] 左移1位，指数+1=${res_exp + 1.U}\n")
      // }.elsewhen(res_mant(MUL_WIDTH - 2) === 1.U) {
      //   normalized_mant := res_mant << 2
      //   normalized_exp := res_exp
      //   printf(p"[Stage2-归一化] 左移2位，指数不变=${res_exp}\n")
      // }.elsewhen(res_mant(MUL_WIDTH - 3) === 1.U) {
      //   normalized_mant := res_mant << 3
      //   normalized_exp := res_exp - 1.U
      //   printf(p"[Stage2-归一化] 左移3位，指数-1=${res_exp - 1.U}\n")
      // }.elsewhen(res_mant(MUL_WIDTH - 4) === 1.U) {
      //   normalized_mant := res_mant << 4
      //   normalized_exp := res_exp - 2.U
      //   printf(p"[Stage2-归一化] 左移4位，指数-2=${res_exp - 2.U}\n")
      // }.elsewhen(res_mant(MUL_WIDTH - 5) === 1.U) {
      //   normalized_mant := res_mant << 5
      //   normalized_exp := res_exp - 3.U
      //   printf(p"[Stage2-归一化] 左移5位，指数-3=${res_exp - 3.U}\n")
      // }.elsewhen(res_mant(MUL_WIDTH - 6) === 1.U) {
      //   normalized_mant := res_mant << 6
      //   normalized_exp := res_exp - 4.U
      //   printf(p"[Stage2-归一化] 左移6位，指数-4=${res_exp - 4.U}\n")
      // }.elsewhen(res_mant(MUL_WIDTH - 7) === 1.U) {
      //   normalized_mant := res_mant << 7
      //   normalized_exp := res_exp - 5.U
      //   printf(p"[Stage2-归一化] 左移7位，指数-5=${res_exp - 5.U}\n")
      // }.elsewhen(res_mant(MUL_WIDTH - 8) === 1.U) {
      //   normalized_mant := res_mant << 8
      //   normalized_exp := res_exp - 6.U
      //   printf(p"[Stage2-归一化] 左移8位，指数-6=${res_exp - 6.U}\n")
      // }.elsewhen(res_mant(MUL_WIDTH - 9) === 1.U) {
      //   normalized_mant := res_mant << 9
      //   normalized_exp := res_exp - 7.U
      //   printf(p"[Stage2-归一化] 左移9位，指数-7=${res_exp - 7.U}\n")
      // }.elsewhen(res_mant(MUL_WIDTH - 10) === 0.U) {
      //   normalized_mant := res_mant << 10
      //   normalized_exp := res_exp - 8.U
      //   printf(p"[Stage2-归一化] 左移10位，指数-8=${res_exp - 8.U}\n")
      // }.otherwise {
      //   normalized_mant := res_mant
      //   normalized_exp := res_exp
      //   printf(p"[Stage2-归一化] 无需左移，指数不变=${res_exp}\n")
      // }
      val leadingZeros = PriorityEncoder(Reverse(stage1.mul_mant)) // 前导零个数
      val shiftAmount = leadingZeros + 1.U

      normalized_mant := stage1.mul_mant << shiftAmount
      normalized_exp := (stage1.mul_exp + 2.U) - shiftAmount

      printf(
        p"[Stage2-Normalization] leading_zeros=${leadingZeros}, left_shift=${shiftAmount}, adjusted_exponent=${normalized_exp}\n"
      )
    }

    stage2.psum := stage1.psum
    stage2.mul_out := Cat(
      stage1.mul_sign,
      normalized_exp(EXP_WIDTH - 1, 0),
      normalized_mant(MUL_WIDTH - 1, MUL_WIDTH - MANT_WIDTH)
    )
    printf(
      p"[Stage2-Output] mul_result_sign=${stage1.mul_sign}, mul_result_exponent=${normalized_exp(EXP_WIDTH - 1, 0)}, mul_result_mantissa=${normalized_mant(MUL_WIDTH - 1, MUL_WIDTH - MANT_WIDTH)}\n"
    )
    printf(p"[Stage2-Output] complete_mul_result=${stage2.mul_out}\n")

    stage2.valid := true.B
  }.otherwise {
    stage2.valid := false.B
  }

  // Stage3: 加法对齐
  val stage3 = Reg(new Bundle {
    val mul_mant = UInt((MANT_WIDTH + 1).W)
    val psum_mant = UInt((MANT_WIDTH + 1).W)
    val common_exp = UInt(FULL_EXP.W)
    val mul_out = UInt(TOTAL_WIDTH.W)
    val psum = UInt(TOTAL_WIDTH.W)
    val valid = Bool()
  })

  // Stage3逻辑：加法对齐
  when(stage2.valid) {
    val (mul_sign, mul_exp, mul_mant) = decodeFP(stage2.mul_out)
    val (psum_sign, psum_exp, psum_mant) = decodeFP(stage2.psum)
    val a_is_zero = (mul_exp === 0.U) && (mul_mant(MANT_WIDTH - 1, 0) === 0.U)
    val b_is_zero = (psum_exp === 0.U) && (psum_mant(MANT_WIDTH - 1, 0) === 0.U)
    val same_exp_diff_sign =
      (mul_exp === psum_exp) && (mul_mant === psum_mant) && (mul_sign =/= psum_sign)

    val exp_ext = UInt((EXP_WIDTH + 1).W)
    val man_ext = UInt((MANT_WIDTH + 1).W)

    // 添加调试打印 - Stage3输入
    printf(
      p"[Stage3-Input] mul_result=${stage2.mul_out}, psum=${stage2.psum}\n"
    )
    printf(
      p"[Stage3-Decode] mul: sign=${mul_sign}, exponent=${mul_exp}, mantissa=${mul_mant}\n"
    )
    printf(
      p"[Stage3-Decode] psum: sign=${psum_sign}, exponent=${psum_exp}, mantissa=${psum_mant}\n"
    )

    stage3.mul_out := stage2.mul_out
    stage3.psum := stage2.psum
    when(mul_exp >= psum_exp) {
      val shift = mul_exp - psum_exp
      stage3.psum_mant := psum_mant >> shift
      stage3.mul_mant := mul_mant
      stage3.common_exp := mul_exp

      printf(
        p"[Stage3-Alignment] mul_exponent is larger, shift psum_mantissa right by ${shift} bits\n"
      )
      printf(
        p"[Stage3-Alignment] after alignment: mul_mantissa=${mul_mant}, psum_mantissa=${psum_mant >> shift}, common_exponent=${mul_exp}\n"
      )

    } otherwise {
      val shift = psum_exp - mul_exp
      stage3.mul_mant := mul_mant >> shift
      stage3.psum_mant := psum_mant
      stage3.common_exp := psum_exp

      printf(
        p"[Stage3-Alignment] psum_exponent is larger, shift mul_mantissa right by ${shift} bits\n"
      )
      printf(
        p"[Stage3-Alignment] after alignment: mul_mantissa=${mul_mant >> shift}, psum_mantissa=${psum_mant}, common_exponent=${psum_exp}\n"
      )
    }

    stage3.valid := true.B
  }.otherwise {
    stage3.valid := false.B
  }

  // Stage4: 加法计算
  val stage4 = Reg(new Bundle {
    val sign = Bool()
    val exp = UInt(FULL_EXP.W)
    val mant = UInt((MANT_WIDTH + 1).W)
    val mul_out = UInt(TOTAL_WIDTH.W)
    val psum = UInt(TOTAL_WIDTH.W)
    val valid = Bool()
  })

  // Stage4逻辑：加法计算
  when(stage3.valid) {
    // 添加调试打印 - Stage4输入
    printf(
      p"[Stage4-Input] aligned_mul_mantissa=${stage3.mul_mant}, aligned_psum_mantissa=${stage3.psum_mant}, common_exponent=${stage3.common_exp}\n"
    )

    stage4.mul_out := stage3.mul_out
    stage4.psum := stage3.psum

    val a_sign = stage3.mul_out(TOTAL_WIDTH - 1)
    val b_sign = stage3.psum(TOTAL_WIDTH - 1)

    when(a_sign === b_sign) {
      // 同号相加
      val sum_fraction =
        Cat(0.U(1.W), stage3.mul_mant) + Cat(0.U(1.W), stage3.psum_mant)
      val cout = sum_fraction(MANT_WIDTH + 1)
      // 如果有进位，需要右移一位并增加指数
      when(cout) {
        stage4.mant := sum_fraction(MANT_WIDTH + 1, 1)
        stage4.exp := stage3.common_exp + 1.U
        printf(
          p"[Stage4-Compute] same signs addition with carry, right shift by 1 bit, new exponent=${stage3.common_exp + 1.U}\n"
        )
      }.otherwise {
        stage4.mant := sum_fraction(MANT_WIDTH, 0)
        stage4.exp := stage3.common_exp
        printf(p"[Stage4-Compute] same signs addition without carry\n")
      }

      stage4.sign := a_sign
      printf(
        p"[Stage4-Compute] same signs addition, result_sign=${a_sign}, result_mantissa=${sum_fraction}\n"
      )
    }.otherwise {
      // 异号相减
      val sub_a = Cat(0.U(1.W), stage3.mul_mant)
      val sub_b = Cat(0.U(1.W), stage3.psum_mant)
      val sub_result = Wire(UInt((MANT_WIDTH + 2).W))
      val cout = Wire(Bool())

      when(a_sign) {
        // A为负数，计算B-A
        sub_result := sub_b - sub_a
        cout := sub_result(MANT_WIDTH + 1) // 借位标志
        printf(
          p"[Stage4-Compute] A is negative, B-A, borrow=${cout}, subtraction_result=${sub_result}\n"
        )
      }.otherwise {
        // A为正数，计算A-B
        sub_result := sub_a - sub_b
        cout := sub_result(MANT_WIDTH + 1) // 借位标志
        printf(
          p"[Stage4-Compute] A is positive, A-B, borrow=${cout}, subtraction_result=${sub_result}\n"
        )
      }

      // 确定结果符号
      stage4.sign := cout

      // 如果有借位，需要取补码
      val abs_result = Wire(UInt((MANT_WIDTH + 1).W))
      when(cout) {
        abs_result := (~sub_result(MANT_WIDTH, 0)) + 1.U
        printf(
          p"[Stage4-Compute] with borrow, take two's complement, result_mantissa=${abs_result}\n"
        )
      }.otherwise {
        abs_result := sub_result(MANT_WIDTH, 0)
        printf(p"[Stage4-Compute] no borrow, result_mantissa=${abs_result}\n")
      }
      // 归一化处理 - 逐位检查
      // when(abs_result(MANT_WIDTH) === 0.U) {
      //   // 需要左移归一化
      //   when(abs_result(MANT_WIDTH - 1) === 1.U) {
      //     stage4.mant := abs_result << 1
      //     stage4.exp := stage3.common_exp - 1.U
      //     printf(p"[Stage4-归一化] 左移1位，新指数=${stage3.common_exp - 1.U}\n")
      //   }.elsewhen(abs_result(MANT_WIDTH - 2) === 1.U) {
      //     stage4.mant := abs_result << 2
      //     stage4.exp := stage3.common_exp - 2.U
      //     printf(p"[Stage4-归一化] 左移2位，新指数=${stage3.common_exp - 2.U}\n")
      //   }.elsewhen(abs_result(MANT_WIDTH - 3) === 1.U) {
      //     stage4.mant := abs_result << 3
      //     stage4.exp := stage3.common_exp - 3.U
      //     printf(p"[Stage4-归一化] 左移3位，新指数=${stage3.common_exp - 3.U}\n")
      //   }.elsewhen(abs_result(MANT_WIDTH - 4) === 1.U) {
      //     stage4.mant := abs_result << 4
      //     stage4.exp := stage3.common_exp - 4.U
      //     printf(p"[Stage4-归一化] 左移4位，新指数=${stage3.common_exp - 4.U}\n")
      //   }.elsewhen(abs_result(MANT_WIDTH - 5) === 1.U) {
      //     stage4.mant := abs_result << 5
      //     stage4.exp := stage3.common_exp - 5.U
      //     printf(p"[Stage4-归一化] 左移5位，新指数=${stage3.common_exp - 5.U}\n")
      //   }.elsewhen(abs_result(MANT_WIDTH - 6) === 1.U) {
      //     stage4.mant := abs_result << 6
      //     stage4.exp := stage3.common_exp - 6.U
      //     printf(p"[Stage4-归一化] 左移6位，新指数=${stage3.common_exp - 6.U}\n")
      //   }.elsewhen(abs_result(MANT_WIDTH - 7) === 1.U) {
      //     stage4.mant := abs_result << 7
      //     stage4.exp := stage3.common_exp - 7.U
      //     printf(p"[Stage4-归一化] 左移7位，新指数=${stage3.common_exp - 7.U}\n")
      //   }.elsewhen(abs_result(MANT_WIDTH - 8) === 1.U) {
      //     stage4.mant := abs_result << 8
      //     stage4.exp := stage3.common_exp - 8.U
      //     printf(p"[Stage4-归一化] 左移8位，新指数=${stage3.common_exp - 8.U}\n")
      //   }.elsewhen(abs_result(MANT_WIDTH - 9) === 1.U) {
      //     stage4.mant := abs_result << 9
      //     stage4.exp := stage3.common_exp - 9.U
      //     printf(p"[Stage4-归一化] 左移9位，新指数=${stage3.common_exp - 9.U}\n")
      //   }.elsewhen(abs_result(0) === 1.U) {
      //     stage4.mant := abs_result << 10
      //     stage4.exp := stage3.common_exp - 10.U
      //     printf(p"[Stage4-归一化] 左移10位，新指数=${stage3.common_exp - 10.U}\n")
      //   }.otherwise {
      //     // 结果为零
      //     stage4.mant := 0.U
      //     stage4.exp := 0.U
      //     stage4.sign := false.B
      //     printf(p"[Stage4-归一化] 结果为零\n")
      //   }
      // }.otherwise {
      //   // 已经归一化
      //   stage4.mant := abs_result
      //   stage4.exp := stage3.common_exp
      //   printf(p"[Stage4-归一化] 无需归一化\n")
      // }
      val leadingZeros = PriorityEncoder(Reverse(abs_result))
      val shiftAmount = leadingZeros
      stage4.mant := abs_result << shiftAmount
      stage4.exp := stage3.common_exp - shiftAmount
      printf(
        p"[Stage4-Normalization] leading_zeros=${leadingZeros}, left_shift=${shiftAmount}, adjusted_exponent=${stage4.exp}\n"
      )
    }

    printf(
      p"[Stage4-Output] sign=${stage4.sign}, exponent=${stage4.exp}, complete_mantissa=${stage4.mant}\n"
    )

    stage4.valid := true.B
  }.otherwise {
    stage4.valid := false.B
  }

  // Stage5: 结果组装
  val stage5 = Reg(new Bundle {
    val out = UInt(TOTAL_WIDTH.W)
    val valid = Bool()
  })

  // Stage5逻辑：结果组装
  when(stage4.valid) {
    val (mul_sign, mul_exp, mul_mant) = decodeFP(stage4.mul_out)
    val (psum_sign, psum_exp, psum_mant) = decodeFP(stage4.psum)
    val a_is_zero = (mul_exp === 0.U) && (mul_mant(MANT_WIDTH - 1, 0) === 0.U)
    val b_is_zero = (psum_exp === 0.U) && (psum_mant(MANT_WIDTH - 1, 0) === 0.U)
    val same_exp_diff_sign =
      (mul_exp === psum_exp) && (mul_mant === psum_mant) && (mul_sign =/= psum_sign)

    when(a_is_zero) {
      // A为零，结果为B
      stage5.out := stage4.psum
      printf(p"[Stage5-Output] A is zero, result is B: ${stage4.psum}\n")
    }.elsewhen(b_is_zero) {
      // B为零，结果为A
      stage5.out := stage4.mul_out
      printf(p"[Stage5-Output] B is zero, result is A: ${stage4.mul_out}\n")
    }.elsewhen(same_exp_diff_sign) {
      // 相同指数不同符号，结果为零
      stage5.out := 0.U
      printf(p"[Stage5-Output] same exponent different signs, result is zero\n")
    }.otherwise {
      // 检查指数是否为零或下溢
      when(stage4.exp(EXP_WIDTH)) {
        // 指数下溢，结果为0
        stage5.out := 0.U
        printf(p"[FPADD-Output] exponent underflow, result is zero\n")
      }.otherwise {
        // 正常情况，组装结果
        val final_mantissa = stage4.mant(MANT_WIDTH - 1, 0)
        stage5.out := Cat(
          stage4.sign,
          stage4.exp(EXP_WIDTH - 1, 0),
          final_mantissa
        )
        printf(
          p"[FPADD-Output] final_result=${Cat(stage4.sign, stage4.exp(EXP_WIDTH - 1, 0), final_mantissa)}, sign=${stage4.sign}, exponent=${stage4
              .exp(EXP_WIDTH - 1, 0)}, mantissa=${final_mantissa}\n"
        )

      }
    }
    stage5.valid := true.B
  }.otherwise {
    stage5.valid := false.B
  }

  // 输出连接
  io.out := stage5.out
  io.valid_out := stage5.valid

  // 最终输出调试打印
  when(stage5.valid) {
    printf(p"[Output] final_result=${stage5.out}, valid=${stage5.valid}\n")
  }
}

// not pass
class FPMAC3(val useHalf: Boolean = false) extends Module {
  // 参数化配置
  val TOTAL_WIDTH = if (useHalf) 16 else 32
  val EXP_WIDTH = if (useHalf) 5 else 8
  val MANT_WIDTH = if (useHalf) 10 else 23
  val BIAS = if (useHalf) 15 else 127

  // 中间位宽定义
  val FULL_MANT = MANT_WIDTH + 1 // 含隐含位
  val MUL_WIDTH = 2 * FULL_MANT // 乘法结果位宽
  val FULL_EXP = EXP_WIDTH + 1 // 用于指数溢出判断

  val io = IO(new Bundle {
    val input = Input(UInt(TOTAL_WIDTH.W))
    val weight = Input(UInt(TOTAL_WIDTH.W))
    val psum = Input(UInt(TOTAL_WIDTH.W))
    val out = Output(UInt(TOTAL_WIDTH.W))
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())
  })

  // 五段流水线寄存器定义
  // Stage1: 乘法分解
  val stage1 = Reg(new Bundle {
    val mul_sign = Bool()
    val mul_exp = UInt(FULL_EXP.W)
    val mul_mant = UInt(MUL_WIDTH.W)
    val psum = UInt(TOTAL_WIDTH.W)
    val valid = Bool()
  })

  // Stage2: 乘法归一化
  val stage2 = Reg(new Bundle {
    val mul_sign = Bool()
    val mul_exp = UInt(EXP_WIDTH.W)
    val mul_mant = UInt(MANT_WIDTH.W)
    val psum = UInt(TOTAL_WIDTH.W)
    val valid = Bool()
  })

  // Stage3: 加法对齐
  val stage3 = Reg(new Bundle {
    val a_sign = Bool()
    val a_mant = UInt((MANT_WIDTH + 2).W) // 包含进位位
    val b_sign = Bool()
    val b_mant = UInt((MANT_WIDTH + 2).W)
    val common_exp = UInt(FULL_EXP.W)
    val valid = Bool()
  })

  // Stage4: 加法计算
  val stage4 = Reg(new Bundle {
    val sign = Bool()
    val exp = UInt(FULL_EXP.W)
    val mant = UInt((MANT_WIDTH + 2).W)
    val valid = Bool()
  })

  // Stage5: 结果组装
  val stage5 = Reg(new Bundle {
    val out = UInt(TOTAL_WIDTH.W)
    val valid = Bool()
  })

  // 初始化寄存器
  // stage1.init(0.U.asTypeOf(stage1))
  // stage2.init(0.U.asTypeOf(stage2))
  // stage3.init(0.U.asTypeOf(stage3))
  // stage4.init(0.U.asTypeOf(stage4))
  // stage5.init(0.U.asTypeOf(stage5))

  // Stage1逻辑：乘法分解
  def decodeFP(data: UInt) = {
    val sign = data(TOTAL_WIDTH - 1)
    val exp = data(TOTAL_WIDTH - 2, MANT_WIDTH)
    val mant = data(MANT_WIDTH - 1, 0)
    val full_mant = Cat(1.U(1.W), mant) // 添加隐含位
    (sign, exp, full_mant)
  }

  when(io.valid_in) {
    val (a_sign, a_exp, a_mant) = decodeFP(io.input)
    val (b_sign, b_exp, b_mant) = decodeFP(io.weight)

    // 检查零
    val a_is_zero = a_exp === 0.U && a_mant === 0.U
    val b_is_zero = b_exp === 0.U && b_mant === 0.U
    val mul_zero = a_is_zero || b_is_zero

    val res_sign = a_sign ^ b_sign
    val res_exp = Mux(mul_zero, 0.U, a_exp +& b_exp - BIAS.U)
    val res_mant = Mux(mul_zero, 0.U, a_mant * b_mant)

    stage1.mul_sign := res_sign
    stage1.mul_exp := res_exp
    stage1.mul_mant := res_mant
    stage1.psum := io.psum
    stage1.valid := true.B
  }.otherwise {
    stage1.valid := false.B
  }

  // Stage2逻辑：乘法归一化
  when(stage1.valid) {
    val normalized_exp = Wire(UInt(FULL_EXP.W))
    val normalized_mant = Wire(UInt(MUL_WIDTH.W))

    when(stage1.mul_mant === 0.U) {
      normalized_exp := 0.U
      normalized_mant := 0.U
    }.otherwise {
      // 关键修复点：正确计算前导1位置和移位量
      // val leadingOne =
      //   (MUL_WIDTH - 1).U - PriorityEncoder(Reverse(stage1.mul_mant))
      // val shiftAmount = (MUL_WIDTH - 1 - MANT_WIDTH).U - leadingOne // 正确移位量计算
      val leadingZeros = PriorityEncoder(Reverse(stage1.mul_mant)) // 前导零个数
      val shiftAmount = leadingZeros + 1.U
      normalized_exp := stage1.mul_exp + 2.U + shiftAmount
      normalized_mant := stage1.mul_mant << shiftAmount
    }

    stage2.mul_sign := stage1.mul_sign
    stage2.mul_exp := normalized_exp(EXP_WIDTH - 1, 0)
    stage2.mul_mant := normalized_mant(
      MUL_WIDTH - 1,
      MUL_WIDTH - MANT_WIDTH // 保留隐含位后的 MANT_WIDTH 位
    )
    stage2.psum := stage1.psum
    stage2.valid := true.B
  }.otherwise {
    stage2.valid := false.B
  }
  // Stage3逻辑：加法对齐
  when(stage2.valid) {
    val (psum_sign, psum_exp, psum_mant) = decodeFP(stage2.psum)
    val mul_exp_ext = Cat(0.U(1.W), stage2.mul_exp)

    // 指数对齐
    val common_exp = Wire(UInt(FULL_EXP.W))
    val a_mant_aligned = Wire(UInt((MANT_WIDTH + 2).W))
    val b_mant_aligned = Wire(UInt((MANT_WIDTH + 2).W))

    when(mul_exp_ext >= psum_exp) {
      val shift = mul_exp_ext - psum_exp
      common_exp := mul_exp_ext
      a_mant_aligned := Cat(0.U(2.W), stage2.mul_mant)
      b_mant_aligned := Cat(0.U(2.W), psum_mant >> shift)
    }.otherwise {
      val shift = psum_exp - mul_exp_ext
      common_exp := psum_exp
      a_mant_aligned := Cat(0.U(2.W), stage2.mul_mant >> shift)
      b_mant_aligned := Cat(0.U(2.W), psum_mant)
    }

    stage3.a_sign := stage2.mul_sign
    stage3.a_mant := a_mant_aligned.asUInt
    stage3.b_sign := psum_sign
    stage3.b_mant := b_mant_aligned.asUInt
    stage3.common_exp := common_exp
    stage3.valid := true.B
  }.otherwise {
    stage3.valid := false.B
  }
  // Stage4逻辑：加法计算
  when(stage3.valid) {
    val a_mant = stage3.a_mant.zext
    val b_mant = stage3.b_mant.zext
    val sum_mant = Mux(
      stage3.a_sign === stage3.b_sign,
      a_mant + b_mant,
      Mux(a_mant >= b_mant, a_mant - b_mant, b_mant - a_mant)
    )

    val res_sign = Mux(
      stage3.a_sign === stage3.b_sign,
      stage3.a_sign,
      Mux(a_mant >= b_mant, stage3.a_sign, stage3.b_sign)
    )

    // 归一化处理
    val leadingOne = PriorityEncoder(Reverse(sum_mant.asUInt))
    val normalized_exp = stage3.common_exp +& ((MANT_WIDTH + 2).U - leadingOne)
    val normalized_mant =
      (sum_mant.asUInt << ((MANT_WIDTH + 2).U - leadingOne))(
        MANT_WIDTH + 1,
        0
      )

    stage4.sign := res_sign
    stage4.exp := normalized_exp
    stage4.mant := normalized_mant
    stage4.valid := true.B
  }.otherwise {
    stage4.valid := false.B
  }

  // Stage5逻辑：结果组装
  when(stage4.valid) {
    val final_exp = Mux(stage4.exp < BIAS.U, 0.U, stage4.exp - BIAS.U)
    val final_mant = stage4.mant(MANT_WIDTH + 1, 2) // 舍入处理简化

    stage5.out := Cat(stage4.sign, final_exp(EXP_WIDTH - 1, 0), final_mant)
    stage5.valid := true.B
  }.otherwise {
    stage5.valid := false.B
  }

  // 输出连接
  io.out := stage5.out
  io.valid_out := stage5.valid

}
