package fpmac

import chisel3._
import chisel3.util._


class INT4MAC extends Module {
  // 定义位宽
  val INPUT_WIDTH = 4  // input和weight的位宽
  val PSUM_WIDTH = 16   // psum的位宽
  val OUTPUT_WIDTH = 16 // 输出位宽

  val io = IO(new Bundle {
    val input = Input(UInt(INPUT_WIDTH.W))
    val weight = Input(UInt(INPUT_WIDTH.W))
    val psum = Input(UInt(PSUM_WIDTH.W))
    val out = Output(UInt(OUTPUT_WIDTH.W))
    val valid_in = Input(Bool())
    val valid_out = Output(Bool())
  })

  // 初始化输出
  io.out := 0.U
  io.valid_out := false.B

  // 乘法结果寄存器
  val mul_result = Reg(UInt((INPUT_WIDTH * 2).W))
  // 加法结果寄存器
  val add_result = Reg(UInt(OUTPUT_WIDTH.W))
  // 有效信号寄存器
  val valid_reg = Reg(Bool())

  when(io.valid_in) {
    // 执行乘法 - 转换为有符号数进行乘法，然后转回无符号数
    val input_signed = io.input.asSInt
    val weight_signed = io.weight.asSInt
    val mul_signed = input_signed * weight_signed
    mul_result := mul_signed.asUInt
    
    // 执行加法 - 转换为有符号数进行加法，然后转回无符号数
    val psum_signed = io.psum.asSInt
    val add_signed = mul_signed + psum_signed
    add_result := add_signed.asUInt
    
    // 设置输出
    io.out := add_result
    io.valid_out := true.B
    
    // // 调试打印
    // printf(p"[INT4MAC] input=${io.input}, weight=${io.weight}, psum=${io.psum}\n")
    // printf(p"[INT4MAC] mul_result=${mul_result}, add_result=${add_result}\n")
    // printf(p"[INT4MAC] final_output=${io.out}\n")
  }.otherwise {
    io.valid_out := false.B
  }
}



