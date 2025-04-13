package fpmac

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// class SystolicMMTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
//   behavior of "SystolicMM"

//   def matrixMultiply(a: Array[Array[Float]], b: Array[Array[Float]]): Array[Array[Float]] = {
//     val n = a.length
//     val result = Array.ofDim[Float](n, n)
//     for (i <- 0 until n) {
//       for (j <- 0 until n) {
//         var sum = 0.0f
//         for (k <- 0 until n) {
//           sum += a(i)(k) * b(k)(j)
//         }
//         result(i)(j) = sum
//       }
//     }
//     result
//   }

//   it should "compute 2x2 matrix multiplication correctly" in {
//     test(new SystolicMM(2, false)) { dut =>
//       // 准备输入数据
//       val a = Array(Array(1.0f, 2.0f), Array(3.0f, 4.0f))
//       val b = Array(Array(5.0f, 6.0f), Array(7.0f, 8.0f))
      
//       // 计算期望结果
//       val expected = matrixMultiply(a, b)
      
//       // 打印输入矩阵
//       println("矩阵A:")
//       a.foreach(row => println(row.mkString(" ")))
//       println("矩阵B:")
//       b.foreach(row => println(row.mkString(" ")))
//       println("期望结果C:")
//       expected.foreach(row => println(row.mkString(" ")))

//       // 重置电路
//       dut.io.reset.poke(true.B)
//       dut.clock.step()
//       dut.io.reset.poke(false.B)
//       dut.clock.step()

//       // 设置输入数据
//       for (i <- 0 until 2) {
//         for (j <- 0 until 2) {
//           dut.io.a_inputs(i)(j).poke(FPConv.float32ToBits(a(i)(j)).U)
//           dut.io.b_inputs(i)(j).poke(FPConv.float32ToBits(b(i)(j)).U)
//         }
//       }

//       // 启动计算
//       dut.io.valid_in.poke(true.B)
//       dut.clock.step()

//       // 等待计算完成
//       var cycles = 0
//       while (!dut.io.valid_out.peekBoolean() && cycles < 100) {
//         dut.clock.step()
//         cycles += 1
//       }

//       println(s"矩阵乘法完成，用时 $cycles 个时钟周期")

//       // 获取结果
//       val result = Array.ofDim[Float](2, 2)
//       for (i <- 0 until 2) {
//         for (j <- 0 until 2) {
//           result(i)(j) = FPConv.bitsToFloat32(dut.io.output_matrix(i)(j).peek().litValue.toInt)
//         }
//       }

//       // 打印实际结果
//       println("实际结果C:")
//       result.foreach(row => println(row.mkString(" ")))

//       // 验证结果
//       for (i <- 0 until 2) {
//         for (j <- 0 until 2) {
//           val expectedValue = expected(i)(j)
//           val actualValue = result(i)(j)
//           val error = math.abs(expectedValue - actualValue) / math.abs(expectedValue)
//           println(f"C[$i][$j]: 期望=$expectedValue%.2f, 实际=$actualValue%.2f, 误差=$error%.6f")
//           assert(error < 1e-5, f"C[$i][$j]中的结果误差过大")
//         }
//       }
//     }
//   }

//   it should "compute 3x3 matrix multiplication correctly" in {
//     test(new SystolicMM(3, false)) { dut =>
//       // 准备输入数据
//       val a = Array(Array(1.0f, 2.0f, 3.0f), Array(4.0f, 5.0f, 6.0f), Array(7.0f, 8.0f, 9.0f))
//       val b = Array(Array(1.0f, 2.0f, 3.0f), Array(4.0f, 5.0f, 6.0f), Array(7.0f, 8.0f, 9.0f))
      
//       // 计算期望结果
//       val expected = matrixMultiply(a, b)
      
//       // 打印输入矩阵
//       println("矩阵A:")
//       a.foreach(row => println(row.mkString(" ")))
//       println("矩阵B:")
//       b.foreach(row => println(row.mkString(" ")))
//       println("期望结果C:")
//       expected.foreach(row => println(row.mkString(" ")))

//       // 重置电路
//       dut.io.reset.poke(true.B)
//       dut.clock.step()
//       dut.io.reset.poke(false.B)
//       dut.clock.step()

//       // 设置输入数据
//       for (i <- 0 until 3) {
//         for (j <- 0 until 3) {
//           dut.io.a_inputs(i)(j).poke(FPConv.float32ToBits(a(i)(j)).U)
//           dut.io.b_inputs(i)(j).poke(FPConv.float32ToBits(b(i)(j)).U)
//         }
//       }

//       // 启动计算
//       dut.io.valid_in.poke(true.B)
//       dut.clock.step()

//       // 等待计算完成
//       var cycles = 0
//       while (!dut.io.valid_out.peekBoolean() && cycles < 100) {
//         dut.clock.step()
//         cycles += 1
//       }

//       println(s"矩阵乘法完成，用时 $cycles 个时钟周期")

//       // 获取结果
//       val result = Array.ofDim[Float](3, 3)
//       for (i <- 0 until 3) {
//         for (j <- 0 until 3) {
//           result(i)(j) = FPConv.bitsToFloat32(dut.io.output_matrix(i)(j).peek().litValue.toInt)
//         }
//       }

//       // 打印实际结果
//       println("实际结果C:")
//       result.foreach(row => println(row.mkString(" ")))

//       // 验证结果
//       for (i <- 0 until 3) {
//         for (j <- 0 until 3) {
//           val expectedValue = expected(i)(j)
//           val actualValue = result(i)(j)
//           val error = math.abs(expectedValue - actualValue) / math.abs(expectedValue)
//           println(f"C[$i][$j]: 期望=$expectedValue%.2f, 实际=$actualValue%.2f, 误差=$error%.6f")
//           assert(error < 1e-5, f"C[$i][$j]中的结果误差过大")
//         }
//       }
//     }
//   }
// }

class OptimizedSystolicMMTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "OptimizedSystolicMM"

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
    test(new OptimizedSystolicMM(2, false)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
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

      // 重置电路
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.clock.step()

      // 设置输入数据 - 按行输入A矩阵，按列输入B矩阵
      for (i <- 0 until 2) {
        // 输入A矩阵的第i行
        for (j <- 0 until 2) {
          dut.io.a_inputs.bits(i)(j).poke(FPConv.float32ToBits(a(i)(j)).U)
        }
        // 输入B矩阵的第i列
        for (j <- 0 until 2) {
          dut.io.b_inputs.bits(j)(i).poke(FPConv.float32ToBits(b(j)(i)).U)
        }
      }
      dut.io.a_inputs.valid.poke(true.B)
      dut.io.b_inputs.valid.poke(true.B)
      dut.clock.step()
      dut.io.a_inputs.valid.poke(false.B)
      dut.io.b_inputs.valid.poke(false.B)

      // 等待计算完成
      var cycles = 0
      while (!dut.io.output_matrix.valid.peekBoolean() && cycles < 50) {
        dut.clock.step()
        cycles += 1
      }

      println(s"矩阵乘法完成，用时 $cycles 个时钟周期")

      // 获取结果
      val result = Array.ofDim[Float](2, 2)
      for (i <- 0 until 2) {
        for (j <- 0 until 2) {
          result(i)(j) = FPConv.bitsToFloat32(dut.io.output_matrix.bits(i)(j).peek().litValue.toInt)
        }
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
    test(new OptimizedSystolicMM(3, false)) { dut =>
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

      // 设置输入数据 - 按行输入A矩阵，按列输入B矩阵
      for (i <- 0 until 3) {
        // 输入A矩阵的第i行
        for (j <- 0 until 3) {
          dut.io.a_inputs.bits(i)(j).poke(FPConv.float32ToBits(a(i)(j)).U)
        }
        // 输入B矩阵的第i列
        for (j <- 0 until 3) {
          dut.io.b_inputs.bits(j)(i).poke(FPConv.float32ToBits(b(j)(i)).U)
        }
      }
      dut.io.a_inputs.valid.poke(true.B)
      dut.io.b_inputs.valid.poke(true.B)
      dut.clock.step()
      dut.io.a_inputs.valid.poke(false.B)
      dut.io.b_inputs.valid.poke(false.B)

      // 等待计算完成
      var cycles = 0
      while (!dut.io.output_matrix.valid.peekBoolean() && cycles < 100) {
        dut.clock.step()
        cycles += 1
      }

      println(s"矩阵乘法完成，用时 $cycles 个时钟周期")

      // 获取结果
      val result = Array.ofDim[Float](3, 3)
      for (i <- 0 until 3) {
        for (j <- 0 until 3) {
          result(i)(j) = FPConv.bitsToFloat32(dut.io.output_matrix.bits(i)(j).peek().litValue.toInt)
        }
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

