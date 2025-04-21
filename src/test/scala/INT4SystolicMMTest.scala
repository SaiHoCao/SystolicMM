package fpmac

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

class INT4SystolicMMTester extends AnyFlatSpec with ChiselScalatestTester {
  "INT4SystolicMM" should "correctly perform 4x4 matrix multiplication" in {
    test(new INT4SystolicMM(4, 4)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
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
      // 预期结果（单位矩阵）
      val expectedResult = Array(
        Array(1, 2, 3, 4),
        Array(5, 6, 7, 8),
        Array(9, 10, 11, 12),
        Array(13, 14, 15, 16)
      )

      // 初始化
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.io.valid_in.poke(false.B)

      // 输入数据
      for (i <- 0 until 4) {
        for (j <- 0 until 4) {
          dut.io.a_in(i).poke(matrixA(i)(j).U)
          dut.io.b_in(i).poke(matrixB(j)(i).U)
        }
      }

      // 开始计算
      dut.io.valid_in.poke(true.B)
      
      // 等待计算完成
      for (_ <- 0 until 20) {
        dut.clock.step()
        if (dut.io.valid_out.peek().litToBoolean) {
          // 验证结果
          for (i <- 0 until 4) {
            for (j <- 0 until 4) {
              dut.io.out(i)(j).expect(expectedResult(i)(j).U)
            }
          }
          dut.io.valid_out.expect(true.B)
        }
      }

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
      dut.io.valid_in.poke(false.B)

      // 输入数据
      for (i <- 0 until 4) {
        for (j <- 0 until 4) {
          dut.io.a_in(i).poke(matrixA2(i)(j).U)
          dut.io.b_in(i).poke(matrixB2(j)(i).U)
        }
      }

      // 开始计算
      dut.io.valid_in.poke(true.B)
      
      // 等待计算完成
      for (_ <- 0 until 20) {
        dut.clock.step()
        if (dut.io.valid_out.peek().litToBoolean) {
          // 验证结果
          for (i <- 0 until 4) {
            for (j <- 0 until 4) {
              dut.io.out(i)(j).expect(expectedResult2(i)(j).U)
            }
          }
          dut.io.valid_out.expect(true.B)
        }
      }

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
      dut.io.valid_in.poke(false.B)

      // 输入数据
      for (i <- 0 until 4) {
        for (j <- 0 until 4) {
          dut.io.a_in(i).poke(matrixA3(i)(j).U)
          dut.io.b_in(i).poke(matrixB3(j)(i).U)
        }
      }

      // 开始计算
      dut.io.valid_in.poke(true.B)
      
      // 等待计算完成
      for (_ <- 0 until 20) {
        dut.clock.step()
        if (dut.io.valid_out.peek().litToBoolean) {
          // 验证结果
          for (i <- 0 until 4) {
            for (j <- 0 until 4) {
              dut.io.out(i)(j).expect(expectedResult3(i)(j).U)
            }
          }
          dut.io.valid_out.expect(true.B)
        }
      }
    }
  }
} 