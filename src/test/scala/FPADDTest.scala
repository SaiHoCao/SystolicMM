package fpmac

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FPADDTest extends AnyFlatSpec with ChiselScalatestTester {
  
  behavior of "FPADD"
  
  // IEEE-754浮点数工具函数
  def floatToUInt(f: Float): BigInt = {
    java.lang.Float.floatToIntBits(f) & 0xFFFFFFFFL
  }
  
  def halfToUInt(h: Float): BigInt = {
    val f_bits = java.lang.Float.floatToIntBits(h)
    val sign = (f_bits >>> 31) & 0x1
    val exp32 = (f_bits >>> 23) & 0xFF
    val frac32 = f_bits & 0x007FFFFF // 23位尾数
    
    // FP16指数转换
    val exp16 = 
      if (exp32 == 0) 0 // 非规格化数
      else if (exp32 >= 0x7F + 16) 0x1F // 上溢
      else if (exp32 <= 0x7F - 15) 0 // 下溢
      else (exp32 - 0x7F + 15) // 正常范围
    
    // FP16尾数截取
    val frac16 = 
      if (exp32 <= 0x7F - 15) 0 // 下溢
      else (frac32 >>> 13) & 0x3FF // 取前10位
    
    (sign << 15) | (exp16 << 10) | frac16
  }
  
  def uintToFloat(u: BigInt): Float = {
    java.lang.Float.intBitsToFloat(u.toInt)
  }
  
  // FP16位表示转换为Float
  def halfUIntToFloat(u: BigInt): Float = {
    // 提取FP16的各部分
    val sign = (u >> 15) & 0x1
    val exp = (u >> 10) & 0x1F
    val frac = u & 0x3FF
    
    // 处理特殊情况
    if (exp == 0) {
      if (frac == 0) {
        // 零值
        return if (sign == 0) 0.0f else -0.0f
      } else {
        // 非规格化数 - 暂不处理，返回小值
        return if (sign == 0) 1e-6f else -1e-6f
      }
    } else if (exp == 31) {
      if (frac == 0) {
        // 无穷大
        return if (sign == 0) Float.PositiveInfinity else Float.NegativeInfinity
      } else {
        // NaN
        return Float.NaN
      }
    }
    
    // 规格化数 - 转换为Float格式
    // FP32指数 = FP16指数 - 15 + 127 = FP16指数 + 112
    val f32_sign = sign << 31
    val f32_exp = (exp + 112) << 23
    val f32_frac = frac << 13
    
    java.lang.Float.intBitsToFloat((f32_sign | f32_exp | f32_frac).toInt)
  }
  
  it should "correctly perform FP16 addition" in {
    test(new FPADD(useHalf = true)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      // 测试不同的半精度浮点数加法组合
      val testCases = Seq(
        (1.0f, 2.0f, 3.0f),       // 1.0 + 2.0 = 3.0
        (2.5f, 3.0f, 5.5f),       // 2.5 + 3.0 = 5.5
        (-1.0f, 2.0f, 1.0f),      // -1.0 + 2.0 = 1.0
        (0.5f, -0.25f, 0.25f),    // 0.5 + (-0.25) = 0.25
        (0.0f, 5.0f, 5.0f),       // 0.0 + 5.0 = 5.0
        (1.5f, 0.0f, 1.5f),       // 1.5 + 0.0 = 1.5
        (-1.5f, -2.5f, -4.0f),    // -1.5 + (-2.5) = -4.0
        (1.5f, -1.5f, 0.0f),      // 1.5 + (-1.5) = 0.0
        (10.0f, 0.0625f, 10.0625f) // 测试对齐操作
      )
      
      // 调试输出函数
      def debugHalf(name: String, value: Float): Unit = {
        val bits = halfToUInt(value)
        println(f"$name%-6s: ${value}%6.2f -> 0x$bits%04x")
      }
      
      for ((floatA, floatB, expected) <- testCases) {
        println(s"\n测试: $floatA + $floatB = $expected")
        debugHalf("floatA", floatA)
        debugHalf("floatB", floatB)
        
        c.io.floatA.poke(halfToUInt(floatA).U)
        c.io.floatB.poke(halfToUInt(floatB).U)
        c.io.valid_in.poke(true.B)
        
        // 等待计算完成 - FPADD需要3个时钟周期
        c.clock.step(3)
        
        val sum_bits = c.io.sum.peek().litValue
        println(f"输出位: 0x$sum_bits%04x")
        
        val result = halfUIntToFloat(sum_bits)
        val error = math.abs(result - expected)
        
        println(f"结果: $result%6.4f (期望: $expected%6.4f, 误差: $error%6.4f)")
        
        // 允许有小的浮点精度误差
        val epsilon = 0.05f
        assert(error < epsilon, s"误差过大: $error")
        
        // 检查分解输出
        val sign_out = c.io.sign_out.peek().litValue == 1
        val exp_out = c.io.exp_out.peek().litValue
        val mant_out = c.io.mant_out.peek().litValue
        
        println(f"符号: ${if (sign_out) "-" else "+"}, 指数: 0x$exp_out%x, 尾数: 0x$mant_out%x")
        
        // 只有在非零结果时才检查符号
        if (expected != 0.0f) {
          val expected_sign = if (expected < 0) 1 else 0
          assert(sign_out == (expected_sign == 1), s"符号不匹配: 期望 $expected_sign, 得到 ${if (sign_out) 1 else 0}")
        }
        
        // 检查valid_out信号
        assert(c.io.valid_out.peek().litValue == 1, "valid_out信号应为true")
      }
    }
  }
  
//   it should "handle special cases correctly" in {
//     test(new FPADD(useHalf = true)) { c =>
//       // 测试特殊情况
      
//       // 1. 零加任何数等于该数
//       c.io.floatA.poke(0x0000.U)  // 0.0
//       c.io.floatB.poke(0x3C00.U)  // 1.0
//       c.io.valid_in.poke(true.B)
//       c.clock.step(3)  // 等待3个时钟周期
//       c.io.sum.expect(0x3C00.U)   // 结果应为1.0
      
//       // 2. 相反数相加等于零
//       c.io.floatA.poke(0x3C00.U)  // 1.0
//       c.io.floatB.poke(0xBC00.U)  // -1.0
//       c.io.valid_in.poke(true.B)
//       c.clock.step(3)  // 等待3个时钟周期
//       c.io.sum.expect(0x0000.U)   // 结果应为0
      
//       // 3. 相同指数不同符号的情况
//       c.io.floatA.poke(0x4000.U)  // 2.0
//       c.io.floatB.poke(0xC000.U)  // -2.0
//       c.io.valid_in.poke(true.B)
//       c.clock.step(3)  // 等待3个时钟周期
//       c.io.sum.expect(0x0000.U)   // 结果应为0
      
//       // 4. 需要对齐的情况 - 大数加小数
//       c.io.floatA.poke(0x4900.U)  // 10.0
//       c.io.floatB.poke(0x1000.U)  // 0.0625 (2^-4)
//       c.io.valid_in.poke(true.B)
//       c.clock.step(3)  // 等待3个时钟周期
      
//       val result1 = c.io.sum.peek().litValue
//       val float_result1 = halfUIntToFloat(result1)
//       println(f"10.0 + 0.0625 = $float_result1%6.4f (0x$result1%04x)")
//       assert(math.abs(float_result1 - 10.0625f) < 0.05f, "对齐计算错误")
      
//       // 5. 需要对齐的情况 - 小数加大数
//       c.io.floatA.poke(0x1000.U)  // 0.0625 (2^-4)
//       c.io.floatB.poke(0x4900.U)  // 10.0
//       c.io.valid_in.poke(true.B)
//       c.clock.step(3)  // 等待3个时钟周期
      
//       val result2 = c.io.sum.peek().litValue
//       val float_result2 = halfUIntToFloat(result2)
//       println(f"0.0625 + 10.0 = $float_result2%6.4f (0x$result2%04x)")
//       assert(math.abs(float_result2 - 10.0625f) < 0.05f, "对齐计算错误")
      
//       // 6. 需要归一化的情况 - 异号相减导致前导零
//       c.io.floatA.poke(0x4000.U)  // 2.0
//       c.io.floatB.poke(0xBC00.U)  // -1.0
//       c.io.valid_in.poke(true.B)
//       c.clock.step(3)  // 等待3个时钟周期
      
//       val result3 = c.io.sum.peek().litValue
//       val float_result3 = halfUIntToFloat(result3)
//       println(f"2.0 + (-1.0) = $float_result3%6.4f (0x$result3%04x)")
//       assert(math.abs(float_result3 - 1.0f) < 0.05f, "归一化计算错误")
//     }
//   }
  
//   it should "correctly handle single precision addition" in {
//     test(new FPADD(useHalf = false)) { c =>
//       // 测试单精度浮点数加法
//       val testCases = Seq(
//         (1.0f, 2.0f, 3.0f),
//         (2.5f, 3.0f, 5.5f),
//         (-1.0f, 2.0f, 1.0f),
//         (0.5f, -0.25f, 0.25f),
//         (1000000.0f, 0.0001f, 1000000.0001f) // 测试精度和对齐
//       )
      
//       for ((floatA, floatB, expected) <- testCases) {
//         println(s"\n测试: $floatA + $floatB = $expected")
        
//         c.io.floatA.poke(floatToUInt(floatA).U)
//         c.io.floatB.poke(floatToUInt(floatB).U)
//         c.io.valid_in.poke(true.B)
        
//         c.clock.step(3)  // 等待3个时钟周期
        
//         val sum_bits = c.io.sum.peek().litValue
//         val result = uintToFloat(sum_bits)
//         val error = math.abs(result - expected)
        
//         println(f"结果: $result%f (期望: $expected%f, 误差: $error%e)")
        
//         // 单精度允许更小的误差
//         val epsilon = 1e-6f
//         assert(error < epsilon, s"误差过大: $error")
//       }
//     }
//   }
} 