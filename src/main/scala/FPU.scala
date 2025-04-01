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
