package fpmac

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

class INT4MACTester extends AnyFlatSpec with ChiselScalatestTester {
  "INT4MAC" should "correctly perform multiply-accumulate operations" in {
    test(new INT4MAC) { dut =>
      // 测试用例1: 正数相乘
      dut.io.input.poke(3.U)      // 3
      dut.io.weight.poke(2.U)     // 2
      dut.io.psum.poke(1.U)       // 1
      dut.io.valid_in.poke(true.B)
      dut.clock.step()
      dut.io.valid_out.expect(true.B)
      dut.io.out.expect(7.U)      // 3*2 + 1 = 7

      // 测试用例2: 负数相乘
      dut.io.input.poke(0xF.U)    // -1 (补码)
      dut.io.weight.poke(0xE.U)   // -2 (补码)
      dut.io.psum.poke(0.U)       // 0
      dut.io.valid_in.poke(true.B)
      dut.clock.step()
      dut.io.valid_out.expect(true.B)
      dut.io.out.expect(2.U)      // (-1)*(-2) + 0 = 2

      // 测试用例3: 正负相乘
      dut.io.input.poke(3.U)      // 3
      dut.io.weight.poke(0xF.U)   // -1
      dut.io.psum.poke(5.U)       // 5
      dut.io.valid_in.poke(true.B)
      dut.clock.step()
      dut.io.valid_out.expect(true.B)
      dut.io.out.expect(2.U)      // 3*(-1) + 5 = 2

      // 测试用例4: 零输入
      dut.io.input.poke(0.U)      // 0
      dut.io.weight.poke(5.U)     // 5
      dut.io.psum.poke(10.U)      // 10
      dut.io.valid_in.poke(true.B)
      dut.clock.step()
      dut.io.valid_out.expect(true.B)
      dut.io.out.expect(10.U)     // 0*5 + 10 = 10

      // 测试用例5: 无效输入
      dut.io.valid_in.poke(false.B)
      dut.clock.step()
      dut.io.valid_out.expect(false.B)
    }
  }
}

class PEInt4Tester extends AnyFlatSpec with ChiselScalatestTester {
  "INT4MAC" should "correctly perform multiply-accumulate operations" in {
    test(new PEInt4) { dut =>
      // 测试用例1: 正数相乘
      dut.io.a_in.poke(3.U)      // 3
      dut.io.b_in.poke(2.U)     // 2
      dut.io.valid_in.poke(true.B)
      dut.clock.step(2)
      dut.io.valid_out.expect(true.B)
      dut.io.out.expect(6.U)   
      dut.io.a_out.expect(3.U) // 输出a_in
      dut.io.b_out.expect(2.U) // 输出b_in

      // 测试用例2: 负数相乘
      dut.io.a_in.poke(0xF.U)    // -1 (补码)
      dut.io.b_in.poke(0xE.U)   // -2 (补码)
      dut.io.valid_in.poke(true.B)
      dut.clock.step(2)
      dut.io.valid_out.expect(true.B)
      dut.io.out.expect(8.U)      // (-1)*(-2) + 0 = 2
      dut.io.a_out.expect(0xF.U) // 输出a_in
      dut.io.b_out.expect(0xE.U) // 输出b_in

      dut.io.reset.poke(true.B)
      dut.clock.step(1)
      dut.io.reset.poke(false.B)

      // 测试用例3: 正负相乘
      dut.io.a_in.poke(3.U)      // 3
      dut.io.b_in.poke(1.U)   // 1
      dut.io.valid_in.poke(true.B)
      dut.clock.step(2)
      dut.io.valid_out.expect(true.B)
      dut.io.out.expect(3.U)      // 
      dut.io.a_out.expect(3.U) // 输出a_in
      dut.io.b_out.expect(1.U) // 输出b_in

    }
  }
}
