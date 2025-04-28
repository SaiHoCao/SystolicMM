package fpmac

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

class INT4GEMMTester extends AnyFlatSpec with ChiselScalatestTester {
  "INT4GEMM" should "correctly perform 4x4 matrix multiplication" in {
    test(new INT4GEMM(4)).withAnnotations(
      Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)
    ) { dut =>
      // 测试用例1: 正数矩阵乘法
      // 矩阵A
      val matrixA = Array(
        Array(1, 2, 3, 4),
        Array(5, 6, 7, 8),
        Array(9, 10, 11, 12),
        Array(13, 14, 15, 10)
      )
      // 矩阵B
      val matrixB = Array(
        Array(1, 0, 0, 0),
        Array(0, 1, 0, 0),
        Array(0, 0, 1, 0),
        Array(0, 0, 0, 1)
      )
      // 预期结果
      val expectedResult = Array(
        Array(1, 2, 3, 4),
        Array(5, 6, 7, 8),
        Array(9, 10, 11, 12),
        Array(13, 14, 15, 10)
      )

      // 初始化
      // dut.io.reset.poke(true.B)
      // dut.clock.step()
      // dut.io.reset.poke(false.B)
      dut.io.out.ready.poke(true.B)

      // 准备输入数据
      val inputA = matrixA.flatten.map(_.U)
      val inputB = matrixB.flatten.map(_.U)

      // 输入数据
      for (i <- 0 until 16) {
        dut.io.in_a.bits(i).poke(inputA(i))
        dut.io.in_b.bits(i).poke(inputB(i))
      }
      dut.io.in_a.valid.poke(true.B)
      dut.io.in_b.valid.poke(true.B)

      while (!dut.io.out.valid.peekBoolean()) {
        dut.clock.step()
      }
      // 验证结果
      for (i <- 0 until 4) {
        for (j <- 0 until 4) {
          val idx = i * 4 + j
          val actual = dut.io.out.bits(idx).peekInt()
          val expected = expectedResult(i)(j)
          println(s"Result[$i][$j]: Actual = $actual, Expected = $expected")
          if (actual != expected) {
            println(
              s"!!! Mismatch at [$i][$j]: Actual = $actual, Expected = $expected"
            )
          }
          dut.io.out.bits(idx).expect(expectedResult(i)(j).U)
        }
      }
    }
  }
}
