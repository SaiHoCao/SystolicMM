package fpmac

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.simulator.VerilatorFlags
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution
import org.scalatest.matchers.should.Matchers

// class FPMACGEMMTest
//     extends AnyFlatSpec
//     with ChiselScalatestTester
//     with ParallelTestExecution {

//   behavior of "FPMAC in GEMM operations"

//   // IEEE-754 浮点数转换工具对象 - 使用Java原生函数
//   object FPConv {
//     // === FP32 转换函数 ===
//     def float32ToBits(f: Float): BigInt = {
//       BigInt(java.lang.Float.floatToIntBits(f)) & 0xffffffffL
//     }

//     def bitsToFloat32(bits: BigInt): Float = {
//       java.lang.Float.intBitsToFloat(bits.toInt)
//     }

//     // === FP16 转换函数 ===
//     def float32ToFP16Bits(f: Float): BigInt = {
//       val bits = java.lang.Float.floatToIntBits(f)

//       // 提取FP32各部分
//       val sign = (bits >>> 31) & 0x1
//       val exp = (bits >>> 23) & 0xff
//       val frac = bits & 0x7fffff

//       // 转换到FP16格式
//       val fp16Sign = sign
//       val fp16Exp = if (exp == 0) {
//         0 // 零或非规格化数
//       } else if (exp >= 0x7f + 16) {
//         31 // 上溢出到Infinity
//       } else if (exp <= 0x7f - 15) {
//         0 // 下溢出到零
//       } else {
//         (exp - 0x7f + 15) & 0x1f // 正常映射
//       }

//       val fp16Frac = if (exp <= 0x7f - 15) {
//         0 // 太小，下溢出到零
//       } else {
//         (frac >> 13) & 0x3ff // 截取高10位
//       }

//       (fp16Sign << 15) | (fp16Exp << 10) | fp16Frac
//     }

//     def fp16BitsToFloat32(bits: BigInt): Float = {
//       // 从FP16位模式提取各部分
//       val sign = ((bits >> 15) & 0x1).toInt
//       val exp = ((bits >> 10) & 0x1f).toInt
//       val frac = (bits & 0x3ff).toInt

//       // 转换到FP32位模式
//       val fp32Bits = if (exp == 0) {
//         if (frac == 0) {
//           // 零值
//           sign << 31
//         } else {
//           // 非规格化数 - 转换为FP32表示
//           val fp32Sign = sign << 31
//           val fp32Frac = frac << 13
//           fp32Sign | fp32Frac
//         }
//       } else if (exp == 0x1f) {
//         if (frac == 0) {
//           // 无穷大
//           (sign << 31) | 0x7f800000
//         } else {
//           // NaN
//           (sign << 31) | 0x7fc00000
//         }
//       } else {
//         // 规格化数 - 转换到FP32格式
//         val fp32Sign = sign << 31
//         val fp32Exp = (exp + 112) << 23 // 127 - 15 = 112
//         val fp32Frac = frac << 13

//         fp32Sign | fp32Exp | fp32Frac
//       }

//       java.lang.Float.intBitsToFloat(fp32Bits)
//     }
//   }

//   /** 执行矩阵乘法操作的软件实现 */
//   def matrixMultiply(
//       A: Array[Array[Float]],
//       B: Array[Array[Float]]
//   ): Array[Array[Float]] = {
//     val m = A.length
//     val n = B(0).length
//     val p = A(0).length
//     require(p == B.length, "矩阵维度不匹配，无法相乘")

//     val C = Array.ofDim[Float](m, n)

//     for (i <- 0 until m) {
//       for (j <- 0 until n) {
//         C(i)(j) = 0.0f
//         for (k <- 0 until p) {
//           C(i)(j) += A(i)(k) * B(k)(j)
//         }
//       }
//     }

//     C
//   }

//   it should "compute simple 2x2 matrix multiplication with FP32" in {
//     test(new FPMAC(useHalf = false)) { c =>
//       // 定义2x2矩阵
//       val A = Array(
//         Array(1.0f, 2.0f),
//         Array(3.0f, 4.0f)
//       )

//       val B = Array(
//         Array(5.0f, 6.0f),
//         Array(7.0f, 8.0f)
//       )

//       // 计算期望结果
//       val expectedC = matrixMultiply(A, B)

//       println("===== 2x2矩阵乘法测试 (FP32) =====")
//       println("矩阵A:")
//       A.foreach(row => println(row.mkString(" ")))
//       println("矩阵B:")
//       B.foreach(row => println(row.mkString(" ")))
//       println("期望结果C:")
//       expectedC.foreach(row => println(row.mkString(" ")))

//       // 使用FPMAC执行乘累加操作
//       val resultC = Array.ofDim[Float](2, 2)

//       for (i <- 0 until 2; j <- 0 until 2) {
//         var acc = 0.0f

//         for (k <- 0 until 2) {
//           println(
//             f"计算 C[$i][$j] += A[$i][$k] * B[$k][$j] = ${A(i)(k)}%.1f * ${B(k)(j)}%.1f + $acc%.1f"
//           )

//           // 设置输入
//           c.io.input.poke(FPConv.float32ToBits(A(i)(k)).U)
//           c.io.weight.poke(FPConv.float32ToBits(B(k)(j)).U)
//           c.io.psum.poke(FPConv.float32ToBits(acc).U)
//           c.io.valid_in.poke(true.B)

//           // 等待流水线完成
//           c.clock.step(5)

//           // 获取结果
//           val resultBits = c.io.out.peek().litValue
//           acc = FPConv.bitsToFloat32(resultBits)

//           println(f"  中间结果: $acc%.2f")
//           c.io.valid_in.poke(false.B)
//           c.clock.step(1)
//         }

//         resultC(i)(j) = acc
//       }

//       println("实际结果C:")
//       resultC.foreach(row => println(row.mkString(" ")))

//       // 验证结果
//       for (i <- 0 until 2; j <- 0 until 2) {
//         val error = math.abs(resultC(i)(j) - expectedC(i)(j))
//         val relError =
//           if (expectedC(i)(j) != 0) error / math.abs(expectedC(i)(j)) else error

//         println(
//           f"C[$i][$j]: 期望=${expectedC(i)(j)}%.2f, 实际=${resultC(i)(j)}%.2f, 误差=$relError%.6f"
//         )
//         assert(relError < 1e-5, s"C[$i][$j]中的结果误差过大")
//       }
//     }
//   }

//   it should "compute matrix multiplication with special values" in {
//     test(new FPMAC(useHalf = false)) { c =>
//       // 使用包含特殊值的矩阵
//       val A = Array(
//         Array(0.0f, -1.5f),
//         Array(2.5f, 0.0f)
//       )

//       val B = Array(
//         Array(-1.0f, 3.0f),
//         Array(0.0f, -2.0f)
//       )

//       // 计算期望结果
//       val expectedC = matrixMultiply(A, B)

//       println("\n===== 包含特殊值的矩阵乘法测试 (FP32) =====")
//       println("矩阵A:")
//       A.foreach(row => println(row.mkString(" ")))
//       println("矩阵B:")
//       B.foreach(row => println(row.mkString(" ")))
//       println("期望结果C:")
//       expectedC.foreach(row => println(row.mkString(" ")))

//       // 使用FPMAC执行乘累加操作
//       val resultC = Array.ofDim[Float](2, 2)

//       for (i <- 0 until 2; j <- 0 until 2) {
//         var acc = 0.0f

//         for (k <- 0 until 2) {
//           println(
//             f"计算 C[$i][$j] += A[$i][$k] * B[$k][$j] = ${A(i)(k)}%.1f * ${B(k)(j)}%.1f + $acc%.1f"
//           )

//           // 设置输入
//           c.io.input.poke(FPConv.float32ToBits(A(i)(k)).U)
//           c.io.weight.poke(FPConv.float32ToBits(B(k)(j)).U)
//           c.io.psum.poke(FPConv.float32ToBits(acc).U)
//           c.io.valid_in.poke(true.B)

//           // 等待流水线完成
//           c.clock.step(5)

//           // 获取结果
//           val resultBits = c.io.out.peek().litValue
//           acc = FPConv.bitsToFloat32(resultBits)

//           println(f"  中间结果: $acc%.2f")
//           c.io.valid_in.poke(false.B)
//           c.clock.step(1)
//         }

//         resultC(i)(j) = acc
//       }

//       println("实际结果C:")
//       resultC.foreach(row => println(row.mkString(" ")))

//       // 验证结果
//       for (i <- 0 until 2; j <- 0 until 2) {
//         val error = math.abs(resultC(i)(j) - expectedC(i)(j))
//         val relError =
//           if (expectedC(i)(j) != 0) error / math.abs(expectedC(i)(j)) else error

//         println(
//           f"C[$i][$j]: 期望=${expectedC(i)(j)}%.2f, 实际=${resultC(i)(j)}%.2f, 误差=$relError%.6f"
//         )
//         assert(relError < 1e-5, s"C[$i][$j]中的结果误差过大")
//       }
//     }
//   }

//   it should "compute simple 2x2 matrix multiplication with FP16" in {
//     test(new FPMAC(useHalf = true)) { c =>
//       // 定义2x2矩阵
//       val A = Array(
//         Array(1.0f, 2.0f),
//         Array(3.0f, 4.0f)
//       )

//       val B = Array(
//         Array(5.0f, 6.0f),
//         Array(7.0f, 8.0f)
//       )

//       // 计算期望结果
//       val expectedC = matrixMultiply(A, B)

//       println("\n===== 2x2矩阵乘法测试 (FP16) =====")
//       println("矩阵A:")
//       A.foreach(row => println(row.mkString(" ")))
//       println("矩阵B:")
//       B.foreach(row => println(row.mkString(" ")))
//       println("期望结果C:")
//       expectedC.foreach(row => println(row.mkString(" ")))

//       // 使用FPMAC执行乘累加操作
//       val resultC = Array.ofDim[Float](2, 2)

//       for (i <- 0 until 2; j <- 0 until 2) {
//         var acc = 0.0f

//         for (k <- 0 until 2) {
//           println(
//             f"计算 C[$i][$j] += A[$i][$k] * B[$k][$j] = ${A(i)(k)}%.1f * ${B(k)(j)}%.1f + $acc%.1f"
//           )

//           // 设置输入 - 注意这里使用FP16位格式
//           c.io.input.poke(FPConv.float32ToFP16Bits(A(i)(k)).U)
//           c.io.weight.poke(FPConv.float32ToFP16Bits(B(k)(j)).U)
//           c.io.psum.poke(FPConv.float32ToFP16Bits(acc).U)
//           c.io.valid_in.poke(true.B)

//           // 等待流水线完成
//           c.clock.step(5)

//           // 获取结果
//           val resultBits = c.io.out.peek().litValue
//           acc = FPConv.fp16BitsToFloat32(resultBits)

//           println(f"  中间结果: $acc%.2f")
//           c.io.valid_in.poke(false.B)
//           c.clock.step(1)
//         }

//         resultC(i)(j) = acc
//       }

//       println("实际结果C:")
//       resultC.foreach(row => println(row.mkString(" ")))

//       // 验证结果 - 注意FP16精度较低，使用较宽松的误差标准
//       for (i <- 0 until 2; j <- 0 until 2) {
//         val error = math.abs(resultC(i)(j) - expectedC(i)(j))
//         val relError =
//           if (expectedC(i)(j) != 0) error / math.abs(expectedC(i)(j)) else error

//         println(
//           f"C[$i][$j]: 期望=${expectedC(i)(j)}%.2f, 实际=${resultC(i)(j)}%.2f, 误差=$relError%.6f"
//         )
//         assert(relError < 0.01, s"C[$i][$j]中的结果误差过大") // FP16精度较低，使用1%的相对误差容限
//       }
//     }
//   }

//   it should "simulate a larger 4x4 GEMM computation with FP32" in {
//     test(new FPMAC(useHalf = false)) { c =>
//       val size = 4

//       // 创建随机矩阵
//       val rand = new scala.util.Random(42) // 固定种子以得到可重复的结果
//       val A =
//         Array.fill(size, size)(rand.nextFloat() * 2.0f - 1.0f) // [-1, 1]范围内的随机值
//       val B = Array.fill(size, size)(rand.nextFloat() * 2.0f - 1.0f)

//       // 计算期望结果
//       val expectedC = matrixMultiply(A, B)

//       println("\n===== 4x4矩阵乘法测试 (FP32) =====")
//       println("使用随机生成的4x4矩阵")

//       // 使用FPMAC执行乘累加操作
//       val resultC = Array.ofDim[Float](size, size)

//       for (i <- 0 until size; j <- 0 until size) {
//         var acc = 0.0f

//         for (k <- 0 until size) {
//           // 设置输入
//           c.io.input.poke(FPConv.float32ToBits(A(i)(k)).U)
//           c.io.weight.poke(FPConv.float32ToBits(B(k)(j)).U)
//           c.io.psum.poke(FPConv.float32ToBits(acc).U)
//           c.io.valid_in.poke(true.B)

//           // 等待流水线完成
//           c.clock.step(5)

//           // 获取结果
//           val resultBits = c.io.out.peek().litValue
//           acc = FPConv.bitsToFloat32(resultBits)

//           c.io.valid_in.poke(false.B)
//           c.clock.step(1)
//         }

//         resultC(i)(j) = acc
//       }

//       // 验证结果
//       var maxRelError = 0.0
//       var totalRelError = 0.0
//       var count = 0

//       for (i <- 0 until size; j <- 0 until size) {
//         val error = math.abs(resultC(i)(j) - expectedC(i)(j))
//         val relError =
//           if (expectedC(i)(j) != 0) error / math.abs(expectedC(i)(j)) else error

//         maxRelError = math.max(maxRelError, relError)
//         totalRelError += relError
//         count += 1

//         // 只打印第一行作为示例
//         if (i == 0) {
//           println(
//             f"C[0][$j]: 期望=${expectedC(0)(j)}%.6f, 实际=${resultC(0)(j)}%.6f, 误差=$relError%.8f"
//           )
//         }

//         assert(relError < 1e-4, s"C[$i][$j]中的结果误差过大")
//       }

//       println(f"最大相对误差: $maxRelError%.8f")
//       println(f"平均相对误差: ${totalRelError / count}%.8f")
//     }
//   }
// }

class GEMMTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "GEMM"

  def matrixMultiply(a: Array[Array[Float]], b: Array[Array[Float]]): Array[Array[Float]] = {
    val n = a.length
    val result = Array.ofDim[Float](n, n)
    for (i <- 0 until n) {
      for (j <- 0 until n) {
        var sum = 0.0f
        for (k <- 0 until n) {
          sum += a(i)(k) * b(k)(j)
        }
        result(i)(j) = sum
      }
    }
    result
  }

  it should "compute 2x2 matrix multiplication correctly" in {
    test(new GEMM(2, false)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      // 准备输入数据
      val a = Array(Array(1.0f, 2.0f), Array(3.0f, 4.0f))
      val b = Array(Array(5.0f, 6.0f), Array(7.0f, 8.0f))
      
      // 计算期望结果
      val expected = matrixMultiply(a, b)
      
      // 打印输入矩阵
      println("矩阵A:")
      a.foreach(row => println(row.mkString(" ")))
      println("矩阵B:")
      b.foreach(row => println(row.mkString(" ")))
      println("期望结果C:")
      expected.foreach(row => println(row.mkString(" ")))

    //   // 重置电路
    //   dut.io.reset.poke(true.B)
    //   dut.clock.step()
    //   dut.io.reset.poke(false.B)
    //   dut.clock.step()

      // 设置输入数据
      val aFlat = a.flatten
      val bFlat = b.flatten
      
      // 设置A矩阵输入
      for (i <- 0 until 4) {
        dut.io.in_a.bits(i).poke(FPConv.float32ToBits(aFlat(i)).U)
      }
      dut.io.in_a.valid.poke(true.B)
      
      // 设置B矩阵输入
      for (i <- 0 until 4) {
        dut.io.in_b.bits(i).poke(FPConv.float32ToBits(bFlat(i)).U)
      }
      dut.io.in_b.valid.poke(true.B)
      
      // 等待输入就绪
      while (!dut.io.in_a.ready.peekBoolean() || !dut.io.in_b.ready.peekBoolean()) {
        dut.clock.step()
      }
      dut.clock.step()
      dut.io.in_a.valid.poke(false.B)
      dut.io.in_b.valid.poke(false.B)

      // 等待计算完成
      var cycles = 0
      while (!dut.io.out.valid.peekBoolean() && cycles < 100) {
        dut.clock.step()
        cycles += 1
      }

      println(s"矩阵乘法完成，用时 $cycles 个时钟周期")

      // 获取结果
      val result = Array.ofDim[Float](2, 2)
      for (i <- 0 until 4) {
        val row = i / 2
        val col = i % 2
        result(row)(col) = FPConv.bitsToFloat32(dut.io.out.bits(i).peek().litValue.toInt)
      }

      // 打印实际结果
      println("实际结果C:")
      result.foreach(row => println(row.mkString(" ")))

      // 验证结果
      for (i <- 0 until 2) {
        for (j <- 0 until 2) {
          val expectedValue = expected(i)(j)
          val actualValue = result(i)(j)
          val error = math.abs(expectedValue - actualValue) / math.abs(expectedValue)
          println(f"C[$i][$j]: 期望=$expectedValue%.2f, 实际=$actualValue%.2f, 误差=$error%.6f")
          assert(error < 1e-5, f"C[$i][$j]中的结果误差过大")
        }
      }
    }
  }

  it should "compute 3x3 matrix multiplication correctly" in {
    test(new GEMM(3, false)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      // 准备输入数据
      val a = Array(Array(1.0f, 2.0f, 3.0f), Array(4.0f, 5.0f, 6.0f), Array(7.0f, 8.0f, 9.0f))
      val b = Array(Array(1.0f, 2.0f, 3.0f), Array(4.0f, 5.0f, 6.0f), Array(7.0f, 8.0f, 9.0f))
      
      // 计算期望结果
      val expected = matrixMultiply(a, b)
      
      // 打印输入矩阵
      println("矩阵A:")
      a.foreach(row => println(row.mkString(" ")))
      println("矩阵B:")
      b.foreach(row => println(row.mkString(" ")))
      println("期望结果C:")
      expected.foreach(row => println(row.mkString(" ")))

      // 重置电路
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.clock.step()

      // 设置输入数据
      val aFlat = a.flatten
      val bFlat = b.flatten
      
      // 设置A矩阵输入
      for (i <- 0 until 9) {
        dut.io.in_a.bits(i).poke(FPConv.float32ToBits(aFlat(i)).U)
      }
      dut.io.in_a.valid.poke(true.B)
      
      // 设置B矩阵输入
      for (i <- 0 until 9) {
        dut.io.in_b.bits(i).poke(FPConv.float32ToBits(bFlat(i)).U)
      }
      dut.io.in_b.valid.poke(true.B)
      
      // 等待输入就绪
      while (!dut.io.in_a.ready.peekBoolean() || !dut.io.in_b.ready.peekBoolean()) {
        dut.clock.step()
      }
      dut.clock.step()
      dut.io.in_a.valid.poke(false.B)
      dut.io.in_b.valid.poke(false.B)

      // 等待计算完成
      var cycles = 0
      while (!dut.io.out.valid.peekBoolean() && cycles < 200) {
        dut.clock.step()
        cycles += 1
      }

      println(s"矩阵乘法完成，用时 $cycles 个时钟周期")

      // 获取结果
      val result = Array.ofDim[Float](3, 3)
      for (i <- 0 until 9) {
        val row = i / 3
        val col = i % 3
        result(row)(col) = FPConv.bitsToFloat32(dut.io.out.bits(i).peek().litValue.toInt)
      }

      // 打印实际结果
      println("实际结果C:")
      result.foreach(row => println(row.mkString(" ")))

      // 验证结果
      for (i <- 0 until 3) {
        for (j <- 0 until 3) {
          val expectedValue = expected(i)(j)
          val actualValue = result(i)(j)
          val error = math.abs(expectedValue - actualValue) / math.abs(expectedValue)
          println(f"C[$i][$j]: 期望=$expectedValue%.2f, 实际=$actualValue%.2f, 误差=$error%.6f")
          assert(error < 1e-5, f"C[$i][$j]中的结果误差过大")
        }
      }
    }
  }
}
