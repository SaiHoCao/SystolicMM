package fpmac

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

class INT4GEMMTester extends AnyFlatSpec with ChiselScalatestTester {
  "INT4GEMM" should "correctly perform 4x4 matrix multiplication" in {
    test(new INT4GEMM(4, 4)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
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
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.io.out.ready.poke(true.B)

      // 准备输入数据
      val inputA = matrixA.flatten.map(_.U)
      val inputB = matrixB.flatten.map(_.U)

      // 输入数据
      dut.io.in_a.bits := VecInit(inputA)
      dut.io.in_b.bits := VecInit(inputB)
      dut.io.in_a.valid.poke(true.B)
      dut.io.in_b.valid.poke(true.B)

      // 等待计算完成
      var resultReceived = false
      for (_ <- 0 until 50) {
        dut.clock.step()
        if (dut.io.out.valid.peek().litToBoolean) {
          // 验证结果
          for (i <- 0 until 4) {
            for (j <- 0 until 4) {
              dut.io.out.bits(i)(j).expect(expectedResult(i)(j).U)
            }
          }
          resultReceived = true
        }
      }
      assert(resultReceived, "Result not received within expected time")

      // 测试用例2: 包含负数的矩阵乘法
      // 矩阵A
      val matrixA2 = Array(
        Array(1, -1, 2, -2),
        Array(3, -3, 4, -4),
        Array(5, -5, 6, -6),
        Array(7, -7, 8, -8)
      )
      // 矩阵B
      val matrixB2 = Array(
        Array(1, 1, 1, 1),
        Array(1, 1, 1, 1),
        Array(1, 1, 1, 1),
        Array(1, 1, 1, 1)
      )
      // 预期结果
      val expectedResult2 = Array(
        Array(0, 0, 0, 0),
        Array(0, 0, 0, 0),
        Array(0, 0, 0, 0),
        Array(0, 0, 0, 0)
      )

      // 重置
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)

      // 准备输入数据
      val inputA2 = matrixA2.flatten.map(_.U)
      val inputB2 = matrixB2.flatten.map(_.U)

      // 输入数据
      dut.io.in_a.bits := VecInit(inputA2)
      dut.io.in_b.bits := VecInit(inputB2)
      dut.io.in_a.valid.poke(true.B)
      dut.io.in_b.valid.poke(true.B)

      // 等待计算完成
      resultReceived = false
      for (_ <- 0 until 50) {
        dut.clock.step()
        if (dut.io.out.valid.peek().litToBoolean) {
          // 验证结果
          for (i <- 0 until 4) {
            for (j <- 0 until 4) {
              dut.io.out.bits(i)(j).expect(expectedResult2(i)(j).U)
            }
          }
          resultReceived = true
        }
      }
      assert(resultReceived, "Result not received within expected time")

      // 测试用例3: 零矩阵乘法
      // 矩阵A
      val matrixA3 = Array.fill(4, 4)(0)
      // 矩阵B
      val matrixB3 = Array.fill(4, 4)(0)
      // 预期结果
      val expectedResult3 = Array.fill(4, 4)(0)

      // 重置
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)

      // 准备输入数据
      val inputA3 = matrixA3.flatten.map(_.U)
      val inputB3 = matrixB3.flatten.map(_.U)

      // 输入数据
      dut.io.in_a.bits := VecInit(inputA3)
      dut.io.in_b.bits := VecInit(inputB3)
      dut.io.in_a.valid.poke(true.B)
      dut.io.in_b.valid.poke(true.B)

      // 等待计算完成
      resultReceived = false
      for (_ <- 0 until 50) {
        dut.clock.step()
        if (dut.io.out.valid.peek().litToBoolean) {
          // 验证结果
          for (i <- 0 until 4) {
            for (j <- 0 until 4) {
              dut.io.out.bits(i)(j).expect(expectedResult3(i)(j).U)
            }
          }
          resultReceived = true
        }
      }
      assert(resultReceived, "Result not received within expected time")
    }
  }
} 