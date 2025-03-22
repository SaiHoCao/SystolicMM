package fpmac

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FPMULTest extends AnyFlatSpec with ChiselScalatestTester {
  
  behavior of "FPMUL"
  
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
  
//   it should "correctly perform FP32 multiplication" in {
//     test(new FPMUL(useHalf = false)) { c =>
//       // 测试不同的浮点数组合
//       val testCases = Seq(
//         (1.0f, 2.0f, 2.0f),     // 1.0 * 2.0 = 2.0
//         (2.5f, 3.0f, 7.5f),     // 2.5 * 3.0 = 7.5
//         (-1.0f, 2.0f, -2.0f),   // -1.0 * 2.0 = -2.0
//         (0.5f, -0.25f, -0.125f), // 0.5 * -0.25 = -0.125
//         (0.0f, 5.0f, 0.0f),     // 0.0 * 5.0 = 0.0
//         (1.5f, 0.0f, 0.0f)      // 1.5 * 0.0 = 0.0
//       )
      
//       for ((inputA, inputB, expected) <- testCases) {
//         // 设置输入值
//         c.io.input.poke(floatToUInt(inputA).U)
//         c.io.inputB.poke(floatToUInt(inputB).U)
        
//         // 等待计算完成
//         c.clock.step(1)
        
//         // 检查结果
//         val result = uintToFloat(c.io.out.peek().litValue)
//         println(f"$inputA%f * $inputB%f = $result%f (expected: $expected%f)")
        
//         // 允许有小的浮点精度误差
//         val epsilon = 1e-5f
//         assert(Math.abs(result - expected) < epsilon, 
//                s"Expected $expected but got $result")
               
//         // 检查分解输出是否正确
//         val sign_out = c.io.sign_out.peek().litValue == 1
//         val exp_out = c.io.exp_out.peek().litValue
//         val mant_out = c.io.mant_out.peek().litValue
        
//         // 只有在非零结果时才检查符号
//         if (expected != 0.0f) {
//           val expected_sign = if (expected < 0) 1 else 0
//           assert(sign_out == (expected_sign == 1), s"Sign mismatch: expected $expected_sign, got ${if (sign_out) 1 else 0}")
//         }
//       }
//     }
//   }
  
  it should "correctly perform FP16 multiplication" in {
    test(new FPMUL(useHalf = true)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      // 测试不同的半精度浮点数组合
      val testCases = Seq(
        (1.0f, 2.0f, 2.0f),     // 1.0 * 2.0 = 2.0
        (2.5f, 3.0f, 7.5f),     // 2.5 * 3.0 = 7.5
        (-1.0f, 2.0f, -2.0f),   // -1.0 * 2.0 = -2.0
        (0.5f, -0.25f, -0.125f), // 0.5 * -0.25 = -0.125
        (0.0f, 5.0f, 0.0f),     // 0.0 * 5.0 = 0.0
        (1.5f, 0.0f, 0.0f)      // 1.5 * 0.0 = 0.0
      )
      
      // 新增调试输出
      def debugHalf(name: String, value: Float): Unit = {
        val bits = halfToUInt(value)
        println(f"$name%-6s: ${value}%6.2f -> 0x$bits%04x")
      }
      
      for ((inputA, inputB, expected) <- testCases) {
        debugHalf("inputA", inputA)
        debugHalf("inputB", inputB)
        c.io.valid_in.poke(true.B)
        c.io.inputA.poke(halfToUInt(inputA).U)
        c.io.inputB.poke(halfToUInt(inputB).U)
        
        c.clock.step(2)
        
        // 检查结果
        c.io.valid_out.expect(true.B)
        val out_bits = c.io.out.peek().litValue
        println(f"Output bits: 0x$out_bits%04x")
        
        val result = halfUIntToFloat(out_bits)
        val error = math.abs(result - expected)
        
        println(f"Result: $result%6.4f (Expected: $expected%6.4f, Error: $error%6.4f)")
        assert(error < 0.05f, s"Excessive error: $error")
        
        // 检查分解输出
        val sign_out = c.io.sign_out.peek().litValue == 1
        val exp_out = c.io.exp_out.peek().litValue
        val mant_out = c.io.mant_out.peek().litValue
        
        println(f"Sign: ${if (sign_out) "-" else "+"}, Exp: 0x$exp_out%x, Mant: 0x$mant_out%x")
        
        // 只有在非零结果时才检查指数和尾数
        if (expected != 0.0f) {
          val expected_sign = if (expected < 0) 1 else 0
          assert(sign_out == (expected_sign == 1), s"Sign mismatch: expected $expected_sign, got ${if (sign_out) 1 else 0}")
        }
      }
    }
  }
  
  // it should "handle special cases correctly" in {
  //   test(new FPMUL(useHalf = true)) { c =>
  //     // 测试特殊情况
      
  //     // 1. 零乘以任何数等于零
  //     c.io.inputA.poke(0x0000.U)  // 0.0
  //     c.io.inputB.poke(0x3C00.U) // 1.0
  //     c.clock.step(2)
  //     c.io.out.expect(0x0000.U)  // 结果应为0
      
  //     // 2. 非规格化数处理
  //     c.io.inputA.poke(0x0001.U)  // 最小的非规格化数
  //     c.io.inputB.poke(0x3C00.U) // 1.0
  //     c.clock.step(2)
  //     // 结果应该是非规格化数或0
  //     assert(c.io.out.peek().litValue <= 0x0001, "非规格化数处理错误")
      
  //     // 3. 指数下溢
  //     c.io.inputA.poke(0x0400.U)  // 非常小的数
  //     c.io.inputB.poke(0x0400.U) // 非常小的数
  //     c.clock.step(2)
  //     c.io.out.expect(0x0000.U)  // 结果应下溢为0
      
  //     // 4. 指数上溢
  //     c.io.inputA.poke(0x7B00.U)  // 非常大的数
  //     c.io.inputB.poke(0x7B00.U) // 非常大的数
  //     c.clock.step(2)
  //     // 检查是否为无穷大或最大值
  //     val result = c.io.out.peek().litValue
  //     assert(result >= 0x7B00, "指数上溢处理错误")
  //   }
  // }
} 