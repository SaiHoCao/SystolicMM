package fpmac

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

class FPMACTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with ParallelTestExecution {

  behavior of "FPMAC"

  // IEEE-754 浮点数转换工具对象 - 使用Java原生函数
  object FPConv {
    // === FP32 转换函数 ===
    def float32ToBits(f: Float): BigInt = {
      BigInt(java.lang.Float.floatToIntBits(f)) & 0xFFFFFFFFL
    }
    
    def bitsToFloat32(bits: BigInt): Float = {
      java.lang.Float.intBitsToFloat(bits.toInt)
    }
    
    // === FP16 转换函数 ===
    def float32ToFP16Bits(f: Float): BigInt = {
      val bits = java.lang.Float.floatToIntBits(f)
      
      // 提取FP32各部分
      val sign = (bits >>> 31) & 0x1
      val exp = (bits >>> 23) & 0xFF
      val frac = bits & 0x7FFFFF
      
      // 转换到FP16格式
      val fp16Sign = sign
      val fp16Exp = if (exp == 0) {
        0 // 零或非规格化数
      } else if (exp >= 0x7F + 16) {
        31 // 上溢出到Infinity
      } else if (exp <= 0x7F - 15) {
        0 // 下溢出到零
      } else {
        (exp - 0x7F + 15) & 0x1F // 正常映射
      }
      
      val fp16Frac = if (exp <= 0x7F - 15) {
        0 // 太小，下溢出到零
      } else {
        (frac >> 13) & 0x3FF // 截取高10位
      }
      
      (fp16Sign << 15) | (fp16Exp << 10) | fp16Frac
    }
    
    def fp16BitsToFloat32(bits: BigInt): Float = {
      // 从FP16位模式提取各部分
      val sign = ((bits >> 15) & 0x1).toInt
      val exp = ((bits >> 10) & 0x1F).toInt
      val frac = (bits & 0x3FF).toInt
      
      // 转换到FP32位模式
      val fp32Bits = if (exp == 0) {
        if (frac == 0) {
          // 零值
          sign << 31
        } else {
          // 非规格化数 - 转换为FP32表示
          val fp32Sign = sign << 31
          val fp32Frac = frac << 13
          fp32Sign | fp32Frac
        }
      } else if (exp == 0x1F) {
        if (frac == 0) {
          // 无穷大
          (sign << 31) | 0x7F800000
        } else {
          // NaN
          (sign << 31) | 0x7FC00000
        }
      } else {
        // 规格化数 - 转换到FP32格式
        val fp32Sign = sign << 31
        val fp32Exp = (exp + 112) << 23 // 127 - 15 = 112
        val fp32Frac = frac << 13
        
        fp32Sign | fp32Exp | fp32Frac
      }
      
      java.lang.Float.intBitsToFloat(fp32Bits)
    }
    
    // 调试辅助函数
    def printFP16(name: String, value: Float): Unit = {
      val bits = float32ToFP16Bits(value)
      println(f"$name%-8s: ${value}%8.4f -> 0x${bits}%04X")
    }
    
    def printFP32(name: String, value: Float): Unit = {
      val bits = float32ToBits(value)
      println(f"$name%-8s: ${value}%8.4f -> 0x${bits}%08X")
    }
  }

  it should "correctly perform FP16 MAC operation" in {
    test(new FPMAC(useHalf = true)) { c =>
      // 测试用例
      val testCases = Seq(
        (1.0f, 2.0f, 0.0f, 2.0f),       // 基本乘加
        (0.5f, 4.0f, 0.25f, 2.25f),     // 小数值
        (1.5f, 1.5f, 0.0f, 2.25f),      // 平方运算
        (-2.0f, 3.0f, 1.0f, -5.0f),     // 负数处理
        (0.0f, 5.0f, 3.0f, 3.0f),       // 零值处理
        (0.125f, 0.25f, 1.0f, 1.03125f) // 小数精度测试
      )
      
      println("===== FP16 MAC 测试 =====")
      
      for ((input, weight, psum, expected) <- testCases) {
        println(s"\n测试: $input * $weight + $psum = $expected")
        FPConv.printFP16("输入", input)
        FPConv.printFP16("权重", weight)
        FPConv.printFP16("累加值", psum)
        FPConv.printFP16("期望值", expected)
        
        // 设置输入
        c.io.input.poke(FPConv.float32ToFP16Bits(input).U)
        c.io.weight.poke(FPConv.float32ToFP16Bits(weight).U)
        c.io.psum.poke(FPConv.float32ToFP16Bits(psum).U)
        c.io.valid_in.poke(true.B)
        
        // 等待流水线完成
        c.clock.step(5)
        
        // 检查结果
        val resultBits = c.io.out.peek().litValue
        val resultFloat = FPConv.fp16BitsToFloat32(resultBits)
        println(f"实际结果: ${resultFloat}%8.6f (0x${resultBits.toInt}%04X)")
        
        // 计算误差
        val error = math.abs(resultFloat - expected)
        val relError = if (expected != 0) error / math.abs(expected) else error
        println(f"绝对误差: ${error}%.6f, 相对误差: ${relError}%.6f")
        
        // FP16精度较低，使用适当的误差阈值
        val errorThreshold = 0.05f
        assert(relError < errorThreshold, 
               s"误差过大: $relError > $errorThreshold, 实际:$resultFloat, 期望:$expected")
        
        // 复位valid信号
        c.io.valid_in.poke(false.B)
        c.clock.step(1)
      }
    }
  }
  
  // it should "correctly perform FP32 MAC operation" in {
  //   test(new FPMAC(useHalf = false)) { c =>
  //     // 测试用例
  //     val testCases = Seq(
  //       (1.0f, 2.0f, 0.0f, 2.0f),           // 基本乘加
  //       (2.5f, 3.0f, 1.5f, 9.0f),           // 一般计算
  //       (-1.0f, 2.0f, 3.0f, 1.0f),          // 负数处理
  //       (0.5f, -0.25f, 10.0f, 9.875f),      // 高精度计算
  //       (1e-6f, 1e6f, 0.5f, 1.5f),          // 大小值相差悬殊
  //       (0.333333f, 3.0f, 0.0f, 1.0f)       // 近似值测试
  //     )
      
  //     println("\n===== FP32 MAC 测试 =====")
      
  //     for ((input, weight, psum, expected) <- testCases) {
  //       println(s"\n测试: $input * $weight + $psum = $expected")
  //       FPConv.printFP32("输入", input)
  //       FPConv.printFP32("权重", weight)
  //       FPConv.printFP32("累加值", psum)
  //       FPConv.printFP32("期望值", expected)
        
  //       // 设置输入
  //       c.io.input.poke(FPConv.float32ToBits(input).U)
  //       c.io.weight.poke(FPConv.float32ToBits(weight).U)
  //       c.io.psum.poke(FPConv.float32ToBits(psum).U)
  //       c.io.valid_in.poke(true.B)
        
  //       // 等待流水线完成
  //       c.clock.step(5)
        
  //       // 检查结果
  //       val resultBits = c.io.out.peek().litValue
  //       val resultFloat = FPConv.bitsToFloat32(resultBits)
  //       println(f"实际结果: ${resultFloat}%12.8f (0x${resultBits.toLong}%08X)")
        
  //       // 计算误差
  //       val error = math.abs(resultFloat - expected)
  //       val relError = if (expected != 0) error / math.abs(expected) else error
  //       println(f"绝对误差: ${error}%.8f, 相对误差: ${relError}%.8f")
        
  //       // FP32精度更高，使用更严格的误差阈值
  //       val errorThreshold = 1e-5f
  //       assert(relError < errorThreshold, 
  //              s"误差过大: $relError > $errorThreshold, 实际:$resultFloat, 期望:$expected")
        
  //       // 复位valid信号
  //       c.io.valid_in.poke(false.B)
  //       c.clock.step(1)
  //     }
  //   }
  // }
  
  // it should "handle valid signal correctly" in {
  //   test(new FPMAC(useHalf = false)) { c =>
  //     // 测试valid信号传播
  //     println("\n===== Valid 信号测试 =====")
      
  //     // 初始状态检查
  //     c.io.valid_in.poke(false.B)
  //     c.clock.step(1)
  //     c.io.valid_out.expect(false.B) // 无输入，输出无效
      
  //     // 发送单个有效输入
  //     c.io.input.poke(FPConv.float32ToBits(1.0f).U)
  //     c.io.weight.poke(FPConv.float32ToBits(2.0f).U)
  //     c.io.psum.poke(FPConv.float32ToBits(3.0f).U)
  //     c.io.valid_in.poke(true.B)
  //     c.clock.step(1)
      
  //     // 在输入有效后立即清除valid信号
  //     c.io.valid_in.poke(false.B)
      
  //     // 检查流水线传播
  //     c.io.valid_out.expect(false.B) // 第1个周期，输出仍然无效
  //     c.clock.step(1)
  //     c.io.valid_out.expect(false.B) // 第2个周期，输出仍然无效
  //     c.clock.step(1)
  //     c.io.valid_out.expect(false.B) // 第3个周期，输出仍然无效
  //     c.clock.step(1)
  //     c.io.valid_out.expect(true.B)  // 第4个周期，输出变为有效
  //     c.clock.step(1)
  //     c.io.valid_out.expect(false.B) // 第5个周期，输出再次变为无效
      
  //     println("有效信号测试通过！")
  //   }
  // }
}
