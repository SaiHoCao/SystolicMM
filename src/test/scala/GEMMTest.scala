// package fpmac

// import chisel3._
// import chisel3.util._
// import chiseltest._
// import org.scalatest.flatspec.AnyFlatSpec
// import org.scalatest.ParallelTestExecution

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

// class GEMMTest
//     extends AnyFlatSpec
//     with ChiselScalatestTester
//     with ParallelTestExecution {

//   behavior of "GEMM Module"
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

//   it should "compute 2x2 matrix multiplication correctly" in {
//     test(new GEMM(2, false)) { c =>
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

//       println("===== GEMM 2x2矩阵乘法测试 (FP32) =====")
//       println("矩阵A:")
//       A.foreach(row => println(row.mkString(" ")))
//       println("矩阵B:")
//       B.foreach(row => println(row.mkString(" ")))
//       println("期望结果C:")
//       expectedC.foreach(row => println(row.mkString(" ")))

//       // 准备输入数据
//       val aInputs = (0 until 2)
//         .flatMap(i => (0 until 2).map(j => FPConv.float32ToBits(A(i)(j))))
//         .toVector
//       val bInputs = (0 until 2)
//         .flatMap(i => (0 until 2).map(j => FPConv.float32ToBits(B(i)(j))))
//         .toVector

//       // 发送输入数据
//       c.io.in_a.valid.poke(true.B)
//       c.io.in_b.valid.poke(true.B)

//       for (i <- 0 until 4) {
//         c.io.in_a.bits(i).poke(aInputs(i).U)
//         c.io.in_b.bits(i).poke(bInputs(i).U)
//       }

//       c.io.reset.poke(false.B)
//       c.io.out.ready.poke(true.B)

//       // 等待数据接收完成
//       c.clock.step(1)
//       c.io.in_a.valid.poke(false.B)
//       c.io.in_b.valid.poke(false.B)

//       // 等待计算完成
//       var cycleCount = 0
//       println(s"${c.io.out.valid.peekBoolean()}")
//       while (!c.io.out.valid.peekBoolean()&& cycleCount < 100) {
//         c.clock.step(1)
//         cycleCount += 1
//       }

//       println(s"矩阵乘法完成，用时 $cycleCount 个时钟周期")

//       // 收集结果
//       val resultC = Array.ofDim[Float](2, 2)
//       for (i <- 0 until 2; j <- 0 until 2) {
//         val resultBits = c.io.out.bits(i)(j).peek().litValue
//         resultC(i)(j) = FPConv.bitsToFloat32(resultBits)
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

//       // 确认数据传输完成
//       c.clock.step(1)
//     }
//   }

//   // it should "compute larger matrix multiplication with random values" in {
//   //   test(new GEMM(4, false)) { c =>
//   //     val size = 4

//   //     // 创建随机矩阵
//   //     val rand = new scala.util.Random(42) // 固定种子以得到可重复的结果
//   //     val A =
//   //       Array.fill(size, size)(rand.nextFloat() * 2.0f - 1.0f) // [-1, 1]范围内的随机值
//   //     val B = Array.fill(size, size)(rand.nextFloat() * 2.0f - 1.0f)

//   //     // 计算期望结果
//   //     val expectedC = matrixMultiply(A, B)

//   //     println("\n===== GEMM 4x4矩阵乘法测试 (FP32) =====")
//   //     println("使用随机生成的4x4矩阵")

//   //     // 准备输入数据
//   //     val aInputs = (0 until size)
//   //       .flatMap(i => (0 until size).map(j => FPConv.float32ToBits(A(i)(j))))
//   //       .toVector
//   //     val bInputs = (0 until size)
//   //       .flatMap(i => (0 until size).map(j => FPConv.float32ToBits(B(i)(j))))
//   //       .toVector

//   //     // 发送输入数据
//   //     c.io.in_a.valid.poke(true.B)
//   //     c.io.in_b.valid.poke(true.B)

//   //     for (i <- 0 until size * size) {
//   //       c.io.in_a.bits(i).poke(aInputs(i).U)
//   //       c.io.in_b.bits(i).poke(bInputs(i).U)
//   //     }

//   //     c.io.reset.poke(false.B)
//   //     c.io.out.ready.poke(true.B)

//   //     // 等待数据接收完成
//   //     c.clock.step(1)
//   //     c.io.in_a.valid.poke(false.B)
//   //     c.io.in_b.valid.poke(false.B)

//   //     // 等待计算完成
//   //     var cycleCount = 0
//   //     while (!c.io.out.valid.peekBoolean() && cycleCount < 100) {
//   //       c.clock.step(1)
//   //       cycleCount += 1
//   //     }

//   //     println(s"矩阵乘法完成，用时 $cycleCount 个时钟周期")

//   //     // 收集结果
//   //     val resultC = Array.ofDim[Float](size, size)
//   //     for (i <- 0 until size; j <- 0 until size) {
//   //       val resultBits = c.io.out.bits(i)(j).peek().litValue
//   //       resultC(i)(j) = FPConv.bitsToFloat32(resultBits)
//   //     }

//   //     // 验证结果
//   //     var maxRelError = 0.0
//   //     var totalRelError = 0.0
//   //     var count = 0

//   //     for (i <- 0 until size; j <- 0 until size) {
//   //       val error = math.abs(resultC(i)(j) - expectedC(i)(j))
//   //       val relError =
//   //         if (expectedC(i)(j) != 0) error / math.abs(expectedC(i)(j)) else error

//   //       maxRelError = math.max(maxRelError, relError)
//   //       totalRelError += relError
//   //       count += 1

//   //       // 只打印第一行作为示例
//   //       if (i == 0) {
//   //         println(
//   //           f"C[0][$j]: 期望=${expectedC(0)(j)}%.6f, 实际=${resultC(0)(j)}%.6f, 误差=$relError%.8f"
//   //         )
//   //       }

//   //       assert(relError < 1e-4, s"C[$i][$j]中的结果误差过大")
//   //     }

//   //     println(f"最大相对误差: $maxRelError%.8f")
//   //     println(f"平均相对误差: ${totalRelError / count}%.8f")

//   //     c.clock.step(1)
//   //   }
//   // }

//   // it should "handle computations with FP16 precision" in {
//   //   test(new GEMM(2, true)) { c =>
//   //     // 定义2x2矩阵
//   //     val A = Array(
//   //       Array(1.0f, 2.0f),
//   //       Array(3.0f, 4.0f)
//   //     )

//   //     val B = Array(
//   //       Array(5.0f, 6.0f),
//   //       Array(7.0f, 8.0f)
//   //     )

//   //     // 计算期望结果
//   //     val expectedC = matrixMultiply(A, B)

//   //     println("\n===== GEMM 2x2矩阵乘法测试 (FP16) =====")
//   //     println("矩阵A:")
//   //     A.foreach(row => println(row.mkString(" ")))
//   //     println("矩阵B:")
//   //     B.foreach(row => println(row.mkString(" ")))
//   //     println("期望结果C:")
//   //     expectedC.foreach(row => println(row.mkString(" ")))

//   //     // 准备输入数据
//   //     val aInputs = (0 until 2)
//   //       .flatMap(i => (0 until 2).map(j => FPConv.float32ToFP16Bits(A(i)(j))))
//   //       .toVector
//   //     val bInputs = (0 until 2)
//   //       .flatMap(i => (0 until 2).map(j => FPConv.float32ToFP16Bits(B(i)(j))))
//   //       .toVector

//   //     // 发送输入数据
//   //     c.io.in_a.valid.poke(true.B)
//   //     c.io.in_b.valid.poke(true.B)

//   //     for (i <- 0 until 4) {
//   //       c.io.in_a.bits(i).poke(aInputs(i).U)
//   //       c.io.in_b.bits(i).poke(bInputs(i).U)
//   //     }

//   //     c.io.reset.poke(false.B)
//   //     c.io.out.ready.poke(true.B)

//   //     // 等待数据接收完成
//   //     c.clock.step(1)
//   //     c.io.in_a.valid.poke(false.B)
//   //     c.io.in_b.valid.poke(false.B)

//   //     // 等待计算完成 - 修改检测逻辑
//   //     var cycleCount = 0
//   //     // 确保等待足够长的时间，至少等待3*n+5个周期
//   //     val minCycles = 3 * 2 + 5 // 对于2x2矩阵
//   //     while ((!c.io.out.valid.peekBoolean() || cycleCount < minCycles) && cycleCount < 100) {
//   //       c.clock.step(1)
//   //       cycleCount += 1
//   //       if (cycleCount % 10 == 0) {
//   //         println(s"已等待 $cycleCount 个周期，valid=${c.io.out.valid.peekBoolean()}")
//   //       }
//   //     }

//   //     println(s"矩阵乘法完成，用时 $cycleCount 个时钟周期")

//   //     // 收集结果
//   //     val resultC = Array.ofDim[Float](2, 2)
//   //     for (i <- 0 until 2; j <- 0 until 2) {
//   //       val resultBits = c.io.out.bits(i)(j).peek().litValue
//   //       resultC(i)(j) = FPConv.fp16BitsToFloat32(resultBits)
//   //     }

//   //     println("实际结果C:")
//   //     resultC.foreach(row => println(row.mkString(" ")))

//   //     // 验证结果 - 注意FP16精度较低，使用较宽松的误差标准
//   //     for (i <- 0 until 2; j <- 0 until 2) {
//   //       val error = math.abs(resultC(i)(j) - expectedC(i)(j))
//   //       val relError =
//   //         if (expectedC(i)(j) != 0) error / math.abs(expectedC(i)(j)) else error

//   //       println(
//   //         f"C[$i][$j]: 期望=${expectedC(i)(j)}%.2f, 实际=${resultC(i)(j)}%.2f, 误差=$relError%.6f"
//   //       )
//   //       assert(relError < 0.01, s"C[$i][$j]中的结果误差过大") // FP16精度较低，使用1%的相对误差容限
//   //     }
//   //   }
//   // }

//   // 复用已有的matrixMultiply函数
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
// }
