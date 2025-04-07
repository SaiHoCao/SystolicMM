package fpmac

object FPConv {
  def float32ToBits(f: Float): Int = {
    java.lang.Float.floatToIntBits(f)
  }

  def bitsToFloat32(bits: Long): Float = {
    java.lang.Float.intBitsToFloat(bits.toInt)
  }
} 