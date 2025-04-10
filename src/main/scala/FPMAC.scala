package fpmac

import chisel3._
import chisel3.util._
import fpmac.{FPADD, FPMUL}

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
  // 参数化配置：根据精度选择不同的位宽
  val TOTAL_WIDTH = if (useHalf) 16 else 32 // 总位宽：半精度16位，单精度32位
  val EXP_WIDTH = if (useHalf) 5 else 8 // 指数位宽：半精度5位，单精度8位
  val MANT_WIDTH = if (useHalf) 10 else 23 // 尾数位宽：半精度10位，单精度23位
  val BIAS = if (useHalf) 15 else 127 // 指数偏移：半精度15，单精度127

  // 中间位宽定义
  val FULL_MANT = MANT_WIDTH + 1 // 含隐含位的尾数位宽
  val MUL_WIDTH = 2 * FULL_MANT // 乘法结果位宽：两个尾数相乘需要2倍位宽
  val FULL_EXP = EXP_WIDTH + 1 // 扩展的指数位宽：多一位用于溢出判断

  // IO接口定义
  val io = IO(new Bundle {
    val input = Input(UInt(TOTAL_WIDTH.W)) // 输入数据
    val weight = Input(UInt(TOTAL_WIDTH.W)) // 权重数据
    val psum = Input(UInt(TOTAL_WIDTH.W)) // 部分和输入
    val out = Output(UInt(TOTAL_WIDTH.W)) // 输出结果
    val valid_in = Input(Bool()) // 输入有效信号
    val valid_out = Output(Bool()) // 输出有效信号
  })
  // 初始化输出
  io.out := 0.U
  io.valid_out := false.B
  // val done = RegInit(false.B)

  // 浮点数解码函数：将32位浮点数分解为符号位、指数位和尾数位
  def decodeFP(data: UInt) = {
    val sign = data(TOTAL_WIDTH - 1) // 符号位：最高位
    val exp = data(TOTAL_WIDTH - 2, MANT_WIDTH) // 指数位：次高位到MANT_WIDTH+1位
    val mant = data(MANT_WIDTH - 1, 0) // 尾数位：低MANT_WIDTH位
    val full_mant = Cat(1.U(1.W), mant) // 添加隐含位1，形成完整尾数
    (sign, exp, full_mant)
  }

  // Stage1: 乘法分解计算阶段
  // 功能：1. 解码输入数据 2. 计算乘法结果 3. 处理特殊情况
  val stage1 = Reg(new Bundle {
    val mul_sign = Bool() // 乘法结果符号
    val mul_exp = UInt(FULL_EXP.W) // 乘法结果指数
    val mul_mant = UInt(MUL_WIDTH.W) // 乘法结果尾数
    val psum = UInt(TOTAL_WIDTH.W) // 部分和输入
    val valid = Bool() // 有效信号
  })

  when(io.valid_in) {
    printf(p"io.valid_in: ${io.valid_in}\n")
    printf(p"stage1.valid: ${stage1.valid}\n")
    // 解码输入数据
    val (a_sign, a_exp, a_mant) = decodeFP(io.input)
    val (b_sign, b_exp, b_mant) = decodeFP(io.weight)

    // 检查特殊情况：输入是否为0
    val a_is_zero = (a_exp === 0.U) && (a_mant(MANT_WIDTH - 1, 0) === 0.U)
    val b_is_zero = (b_exp === 0.U) && (b_mant(MANT_WIDTH - 1, 0) === 0.U)
    val mul_zero = a_is_zero || b_is_zero

    // 计算乘法结果
    val res_sign = a_sign ^ b_sign // 符号位异或
    val res_exp = Mux(mul_zero, 0.U, a_exp +& b_exp - BIAS.U) // 指数相加并减去偏移
    val res_mant = Mux(mul_zero, 0.U, a_mant * b_mant) // 尾数相乘

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

    // 保存Stage1结果
    stage1.mul_sign := res_sign
    stage1.mul_exp := res_exp
    stage1.mul_mant := res_mant
    stage1.psum := io.psum
    stage1.valid := true.B
  }.otherwise {
    stage1.valid := false.B
  }

  // Stage2: 乘法归一化输出阶段
  // 功能：1. 对乘法结果进行归一化 2. 调整指数和尾数
  val stage2 = Reg(new Bundle {
    val mul_out = UInt(TOTAL_WIDTH.W) // 归一化后的乘法结果
    val psum = UInt(TOTAL_WIDTH.W) // 部分和输入
    val valid = Bool() // 有效信号
  })

  when(stage1.valid) {
    val normalized_exp = Wire(UInt(FULL_EXP.W)) // 归一化后的指数，EXP_WIDTH + 1
    val normalized_mant = Wire(UInt(MUL_WIDTH.W)) // 归一化后的尾数，2*(MANT_WIDTH + 1)

    printf(p"stage2.valid: ${stage2.valid}\n")
    // 添加调试打印 - Stage2输入
    printf(
      p"[Stage2-Input] mul_sign=${stage1.mul_sign}, mul_exponent=${stage1.mul_exp}, mul_mantissa=${stage1.mul_mant}\n"
    )

    when(stage1.mul_mant === 0.U) { // 处理零的情况
      normalized_exp := 0.U
      normalized_mant := 0.U
      printf(p"[Stage2-Normalization] mantissa is zero, result is zero\n")
    }.otherwise {
      // 计算前导零个数并确定移位量
      val leadingZeros = PriorityEncoder(Reverse(stage1.mul_mant))
      val shiftAmount = leadingZeros

      // 归一化处理：左移尾数并调整指数
      normalized_mant := stage1.mul_mant << shiftAmount
      normalized_exp := (stage1.mul_exp + 1.U) - shiftAmount // stage1.mul_exp + 1.U 消除2*(MANT_WIDTH + 1)的隐含位

      printf(
        p"[Stage2-Normalization] leading_zeros=${leadingZeros}, left_shift=${shiftAmount}, adjusted_exponent=${normalized_exp}\n"
      )
    }

    // 保存Stage2结果
    stage2.psum := stage1.psum
    stage2.mul_out := Cat(
      stage1.mul_sign,
      normalized_exp(EXP_WIDTH - 1, 0),
      normalized_mant(MUL_WIDTH - 2, MUL_WIDTH - 1 - MANT_WIDTH) // 不包含隐含位1
    )
    printf(p"[Stage2-Output] complete_mul_result=${stage2.mul_out}\n")

    stage2.valid := true.B
  }.otherwise {
    stage2.valid := false.B
  }

  // Stage3: 加法对齐阶段
  // 功能：1. 解码乘法结果和部分和 2. 对齐指数 3. 准备加法运算
  val stage3 = Reg(new Bundle {
    val mul_mant = UInt((MANT_WIDTH + 1).W) // 对齐后的乘法结果尾数
    val psum_mant = UInt((MANT_WIDTH + 1).W) // 对齐后的部分和尾数
    val common_exp = UInt(FULL_EXP.W) // 对齐后的公共指数
    val mul_out = UInt(TOTAL_WIDTH.W) // 乘法结果
    val psum = UInt(TOTAL_WIDTH.W) // 部分和
    val valid = Bool() // 有效信号
  })

  // Stage3逻辑：加法对齐 
  when(stage2.valid) {
    printf(p"stage3.valid: ${stage3.valid}\n")
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

    // 对齐数量级，向高数量级移动
    stage3.mul_out := stage2.mul_out
    stage3.psum := stage2.psum
    when(mul_exp >= psum_exp) {
      // 乘法结果指数大于等于部分和指数，需要右移部分和
      val shift = mul_exp - psum_exp
      stage3.psum_mant := psum_mant >> shift
      stage3.mul_mant := mul_mant
      stage3.common_exp := mul_exp // psum_exp + mul_exp - psum_exp

      printf(
        p"[Stage3-Alignment] mul_exponent is larger, shift psum_mantissa right by ${shift} bits\n"
      )
    } otherwise {
      // 部分和指数大于乘法结果指数，需要右移乘法结果
      val shift = psum_exp - mul_exp
      stage3.mul_mant := mul_mant >> shift
      stage3.psum_mant := psum_mant
      stage3.common_exp := psum_exp // mul_exp + psum_exp - mul_exp

      printf(
        p"[Stage3-Alignment] psum_exponent is larger, shift mul_mantissa right by ${shift} bits\n"
      )
    }

    stage3.valid := true.B
  }.otherwise {
    stage3.valid := false.B
  }

  // Stage4: 加法计算阶段
  // 功能：1. 执行尾数加法 2. 处理进位 3. 确定结果符号
  val stage4 = Reg(new Bundle {
    val sign = Bool() // 结果符号
    val exp = UInt((EXP_WIDTH + 1).W) // 结果指数，多一位判断溢出
    val mant = UInt((MANT_WIDTH + 1).W) // 结果尾数,包含隐含位
    val mul_out = UInt(TOTAL_WIDTH.W) // 乘法结果
    val psum = UInt(TOTAL_WIDTH.W) // 部分和
    val valid = Bool() // 有效信号
  })

  // Stage4逻辑：加法计算
  when(stage3.valid) {
    // 添加调试打印 - Stage4输入
    printf(
      p"[Stage4-Input] aligned_mul_mantissa=${stage3.mul_mant}, aligned_psum_mantissa=${stage3.psum_mant}, common_exponent=${stage3.common_exp}\n"
    )
    printf(p"stage4.valid: ${stage4.valid}\n")
    stage4.mul_out := stage3.mul_out
    stage4.psum := stage3.psum

    val a_sign = stage3.mul_out(TOTAL_WIDTH - 1)
    val b_sign = stage3.psum(TOTAL_WIDTH - 1)

    when(a_sign === b_sign) {
      // 同号相加：直接相加，处理进位
      val sum_fraction =
        Cat(0.U(1.W), stage3.mul_mant) + Cat(
          0.U(1.W),
          stage3.psum_mant
        ) // MANT_WIDTH + 1 + 1 位，比隐含位多一位判断进位
      val cout = sum_fraction(MANT_WIDTH + 1)
      when(cout) {
        // 有进位：右移一位，指数加1
        stage4.mant := sum_fraction(MANT_WIDTH + 1, 1) // 取前MANT_WIDTH + 1位
        stage4.exp := stage3.common_exp + 1.U
        printf(
          p"[Stage4-Compute] same signs addition with carry, right shift by 1 bit\n"
        )
      }.otherwise {
        // 无进位：直接使用结果
        stage4.mant := sum_fraction(MANT_WIDTH, 0) // 取后MANT_WIDTH + 1位
        stage4.exp := stage3.common_exp
        printf(p"[Stage4-Compute] same signs addition without carry\n")
      }
      stage4.sign := a_sign
    }.otherwise {
      // 异号相减：需要处理借位和符号
      val sub_a =
        Cat(0.U(1.W), stage3.mul_mant) // MANT_WIDTH + 1 + 1 位，比隐含位多一位判断进位
      val sub_b = Cat(0.U(1.W), stage3.psum_mant)
      val sub_result = Wire(UInt((MANT_WIDTH + 2).W))
      val cout = Wire(Bool())

      when(a_sign) {
        // A为负数：计算B-A
        sub_result := sub_b - sub_a
        cout := sub_result(MANT_WIDTH + 1) // 最高位为借位
        printf(p"[Stage4-Compute] A is negative, B-A, borrow=${cout}\n")
      }.otherwise {
        // A为正数：计算A-B
        sub_result := sub_a - sub_b
        cout := sub_result(MANT_WIDTH + 1)
        printf(p"[Stage4-Compute] A is positive, A-B, borrow=${cout}\n")
      }

      // 确定结果符号和绝对值
      stage4.sign := cout // 借位为负 不借位为正
      val abs_result = Wire(UInt((MANT_WIDTH + 1).W))
      when(cout) {
        abs_result := (~sub_result(MANT_WIDTH, 0)) + 1.U // 取原值
        printf(p"[Stage4-Compute] with borrow, take two's complement\n")
      }.otherwise {
        abs_result := sub_result(MANT_WIDTH, 0) // 正常取后MANT_WIDTH + 1位
        printf(p"[Stage4-Compute] no borrow\n")
      }

      // 归一化处理：计算前导零并左移
      val leadingZeros = PriorityEncoder(Reverse(abs_result))
      val shiftAmount = leadingZeros
      stage4.mant := abs_result << shiftAmount // 包含隐含位
      stage4.exp := stage3.common_exp - shiftAmount
      printf(
        p"[Stage4-Normalization] leading_zeros=${leadingZeros}, left_shift=${shiftAmount}\n"
      )
    }

    printf(
      p"[Stage4-Output] sign=${stage4.sign}, exponent=${stage4.exp}, mantissa=${stage4.mant}\n"
    )
    stage4.valid := true.B
  }.otherwise {
    stage4.valid := false.B
  }

  // Stage5: 结果组装阶段
  // 功能：1. 处理特殊情况 2. 组装最终结果
  val stage5 = Reg(new Bundle {
    val out = UInt(TOTAL_WIDTH.W) // 最终输出结果
    val valid = Bool() // 有效信号
  })

  // Stage5逻辑：结果组装
  when(stage4.valid) {
    printf(p"stage5.valid: ${stage5.valid}\n")
    val (mul_sign, mul_exp, mul_mant) = decodeFP(stage4.mul_out)
    val (psum_sign, psum_exp, psum_mant) = decodeFP(stage4.psum)
    val a_is_zero = (mul_exp === 0.U) && (mul_mant(MANT_WIDTH - 1, 0) === 0.U)
    val b_is_zero = (psum_exp === 0.U) && (psum_mant(MANT_WIDTH - 1, 0) === 0.U)
    val same_exp_diff_sign =
      (mul_exp === psum_exp) && (mul_mant === psum_mant) && (mul_sign =/= psum_sign)

    when(a_is_zero) {
      // A为零：结果为B
      stage5.out := stage4.psum
      printf(p"[Stage5-Output] A is zero, result is B: ${stage4.psum}\n")
    }.elsewhen(b_is_zero) {
      // B为零：结果为A
      stage5.out := stage4.mul_out
      printf(p"[Stage5-Output] B is zero, result is A: ${stage4.mul_out}\n")
    }.elsewhen(same_exp_diff_sign) {
      // 相同指数不同符号：结果为零
      stage5.out := 0.U
      printf(p"[Stage5-Output] same exponent different signs, result is zero\n")
    }.otherwise {
      when(stage4.exp(EXP_WIDTH)) {
        // 指数下溢：结果为0
        stage5.out := 0.U
        printf(p"[FPADD-Output] exponent underflow, result is zero\n")
      }.otherwise {
        // 正常情况：组装最终结果
        val final_mantissa = stage4.mant(MANT_WIDTH - 1, 0)
        stage5.out := Cat(
          stage4.sign,
          stage4.exp(EXP_WIDTH - 1, 0),
          final_mantissa
        )
        printf(
          p"[FPADD-Output] final_result=${Cat(stage4.sign, stage4.exp(EXP_WIDTH - 1, 0), final_mantissa)}\n"
        )
      }
    }
    stage5.valid := true.B
    stage4.valid := false.B
    stage3.valid := false.B
    stage2.valid := false.B
    stage1.valid := false.B
  }.otherwise {
    stage5.valid := false.B
  }

  // 输出连接
  io.out := stage5.out
  io.valid_out := stage5.valid 
  // 最终输出调试打印
  // when(stage5.valid) {
  //   printf(p"[Output] final_result=${stage5.out}, valid=${stage5.valid}\n")
  // }
}
