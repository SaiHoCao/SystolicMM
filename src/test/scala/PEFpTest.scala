package fpmac

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PEFpTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "PEFp"

  it should "perform basic multiplication" in {
    test(new PEFp(useHalf = false)) { dut =>
      // 初始化
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.clock.step()

      // 设置输入数据
      val a = 0x3f800000L // 1.0 in IEEE 754
      val b = 0x40000000L // 2.0 in IEEE 754

      // 发送输入数据
      dut.io.a_in.valid.poke(true.B)
      dut.io.b_in.valid.poke(true.B)
      dut.io.a_in.bits.poke(a.U)
      dut.io.b_in.bits.poke(b.U)
      dut.clock.step()

      // 等待计算完成
      while (!dut.io.out.valid.peek().litToBoolean) {
        dut.clock.step()
      }

      dut.io.a_out.ready.poke(true.B)
      dut.io.b_out.ready.poke(true.B)
      dut.io.out.ready.poke(true.B)
      // 验证输出
      val result = dut.io.out.bits.peek().litValue
      println(f"Result: 0x$result%X")
    }
  }

  it should "handle half precision" in {
    test(new PEFp(useHalf = true)) { dut =>
      // 初始化
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.clock.step()

      // 设置输入数据 (half precision)
      val a = 0x3c00L // 1.0 in IEEE 754 half precision
      val b = 0x4000L // 2.0 in IEEE 754 half precision

      // 发送输入数据
      dut.io.a_in.valid.poke(true.B)
      dut.io.b_in.valid.poke(true.B)
      dut.io.a_in.bits.poke(a.U)
      dut.io.b_in.bits.poke(b.U)
      dut.clock.step()

      // 等待计算完成
      while (!dut.io.out.valid.peek().litToBoolean) {
        dut.clock.step()
      }
      dut.io.a_out.ready.poke(true.B)
      dut.io.b_out.ready.poke(true.B)
      dut.io.out.ready.poke(true.B)
      // 验证输出
      val result = dut.io.out.bits.peek().litValue
      println(f"Result: 0x$result%X")
    }
  }

  it should "propagate data through the systolic array" in {
    test(new PEFp(useHalf = false)) { dut =>
      // 初始化
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.clock.step()

      // 设置输入数据
      val a = 0x3f800000L // 1.0
      val b = 0x40000000L // 2.0

      // 发送输入数据
      dut.io.a_in.valid.poke(true.B)
      dut.io.b_in.valid.poke(true.B)
      dut.io.a_in.bits.poke(a.U)
      dut.io.b_in.bits.poke(b.U)
      dut.clock.step()

      // 等待计算完成
      while (!dut.io.out.valid.peek().litToBoolean) {
        dut.clock.step()
      }
      dut.io.a_out.ready.poke(true.B)
      dut.io.b_out.ready.poke(true.B)
      dut.io.out.ready.poke(true.B)
      // 验证数据传播
      dut.io.a_out.valid.expect(true.B)
      dut.io.b_out.valid.expect(true.B)
      dut.io.a_out.bits.expect(a.U)
      dut.io.b_out.bits.expect(b.U)
    }
  }

  it should "perform accumulation correctly" in {
    test(new PEFp(useHalf = false)) { dut =>
      // 初始化
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.clock.step()

      // 第一组数据：1.0 * 2.0 = 2.0
      val a1 = 0x3f800000L // 1.0
      val b1 = 0x40000000L // 2.0
      
      // 发送第一组数据
      dut.io.a_in.valid.poke(true.B)
      dut.io.b_in.valid.poke(true.B)
      dut.io.a_in.bits.poke(a1.U)
      dut.io.b_in.bits.poke(b1.U)
      dut.clock.step()

      // 等待第一次计算完成
      while (!dut.io.out.valid.peek().litToBoolean) {
        dut.clock.step()
      }
      dut.io.a_out.ready.poke(true.B)
      dut.io.b_out.ready.poke(true.B)
      dut.io.out.ready.poke(true.B)
      dut.clock.step()

      // 第二组数据：2.0 * 3.0 = 6.0
      val a2 = 0x40000000L // 2.0
      val b2 = 0x40400000L // 3.0
      
      // 发送第二组数据
      dut.io.a_in.valid.poke(true.B)
      dut.io.b_in.valid.poke(true.B)
      dut.io.a_in.bits.poke(a2.U)
      dut.io.b_in.bits.poke(b2.U)
      dut.clock.step()

      // 等待第二次计算完成
      while (!dut.io.out.valid.peek().litToBoolean) {
        dut.clock.step()
      }
      dut.io.a_out.ready.poke(true.B)
      dut.io.b_out.ready.poke(true.B)
      dut.io.out.ready.poke(true.B)

      // 验证累加结果
      val result = dut.io.out.bits.peek().litValue
      println(f"Accumulation Result: 0x$result%X")
      // 期望结果应该是 2.0 + 6.0 = 8.0
      // 8.0 的 IEEE 754 表示是 0x41000000
      dut.io.out.bits.expect(0x41000000L.U)
    }
  }

  it should "perform half precision accumulation correctly" in {
    test(new PEFp(useHalf = true)) { dut =>
      // 初始化
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.clock.step()

      // 第一组数据：1.0 * 2.0 = 2.0
      val a1 = 0x3c00L // 1.0 in half precision
      val b1 = 0x4000L // 2.0 in half precision
      
      // 发送第一组数据
      dut.io.a_in.valid.poke(true.B)
      dut.io.b_in.valid.poke(true.B)
      dut.io.a_in.bits.poke(a1.U)
      dut.io.b_in.bits.poke(b1.U)
      dut.clock.step()

      // 等待第一次计算完成
      while (!dut.io.out.valid.peek().litToBoolean) {
        dut.clock.step()
      }
      dut.io.a_out.ready.poke(true.B)
      dut.io.b_out.ready.poke(true.B)
      dut.io.out.ready.poke(true.B)
      dut.clock.step()

      // 第二组数据：2.0 * 3.0 = 6.0
      val a2 = 0x4000L // 2.0 in half precision
      val b2 = 0x4200L // 3.0 in half precision
      
      // 发送第二组数据
      dut.io.a_in.valid.poke(true.B)
      dut.io.b_in.valid.poke(true.B)
      dut.io.a_in.bits.poke(a2.U)
      dut.io.b_in.bits.poke(b2.U)
      dut.clock.step()

      // 等待第二次计算完成
      while (!dut.io.out.valid.peek().litToBoolean) {
        dut.clock.step()
      }
      dut.io.a_out.ready.poke(true.B)
      dut.io.b_out.ready.poke(true.B)
      dut.io.out.ready.poke(true.B)

      // 验证累加结果
      val result = dut.io.out.bits.peek().litValue
      println(f"Half Precision Accumulation Result: 0x$result%X")
      // 期望结果应该是 2.0 + 6.0 = 8.0
      // 8.0 的 IEEE 754 half precision 表示是 0x4800
      dut.io.out.bits.expect(0x4800L.U)
    }
  }
}
