package me.lemire;

import org.openjdk.jmh.annotations.Benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.Random;

@Measurement(iterations = 10, time = 1)
@Warmup(iterations = 5, time = 1)
// the default size of char[] is enough to trigger OSR compilation which is not as effective as C2
// as most of the time, in the real world, is likely the method will be compiled with way less iterations of the loop
// making the compiler to perform better decisions i.e. silly_tableX reference shouldn't be brought into a register
// in the hot path but hoisted before the loop begins, given that's a constant value, from the JVM pov
@Fork(value = 2, jvmArgsPrepend = {"-XX:-UseOnStackReplacement"})
public class MyBenchmark {
    private static final byte[] silly_table1;

    static {
        silly_table1 = new byte[256];
        silly_table1['\\'] = '\\';
    }
    private static final byte[] silly_table2;

    static {
        silly_table2 = new byte[256];
        silly_table2['\\'] = '\\';
        silly_table2['\n'] = 'n';
    }

    private static final byte[] silly_table3;

    static {
        silly_table3 = new byte[256];
        silly_table3['\\'] = '\\';
        silly_table3['\n'] = 'n';
        silly_table3['\t'] = 't';
    }

    public static int replaceBackslash1(char[] original, char[] newArray) {
        int index = 0;
        for (char c : original) {
            if (c == '\\') {
                newArray[index++] = '\\';
                newArray[index++] = '\\';
            } else {
                newArray[index++] = c;
            }
        }
        return index;
    }

    public static int replaceBackslashTable1(char[] original, char[] newArray) {
        int newArrayLength = 0;
        for (char c : original) {
            // copy regardless into output; this allow the loop to be unrolled ;)
            newArray[newArrayLength] = c;
            byte b = silly_table1[c % 256];
            if (c < 256 && b != 0) {
                // we need to copy from the last known index to the current one, excluded
                newArray[newArrayLength] = '\\';
                newArray[newArrayLength + 1] = (char) b;
                newArrayLength += 2;
            } else {
                newArrayLength++;
            }
        }
        return newArrayLength;
    }


    public static int replaceBackslash2(char[] original, char[] newArray) {
        int index = 0;
        for (char c : original) {
            if (c == '\\') {
                newArray[index++] = '\\';
                newArray[index++] = '\\';
            } else if (c == '\n') {
                newArray[index++] = '\\';
                newArray[index++] = 'n';
            } else {
                newArray[index++] = c;
            }
        }
        return index;
    }

    public static int replaceBackslashTable2(char[] original, char[] newArray) {
        int newArrayLength = 0;
        for (char c : original) {
            // copy regardless into output; this allow the loop to be unrolled ;)
            newArray[newArrayLength] = c;
            byte b = silly_table2[c % 256];
            if (c < 256 && b != 0) {
                // we need to copy from the last known index to the current one, excluded
                newArray[newArrayLength] = '\\';
                newArray[newArrayLength + 1] = (char) b;
                newArrayLength += 2;
            } else {
                newArrayLength++;
            }
        }
        return newArrayLength;
    }

    public static int replaceBackslash3(char[] original, char[] newArray) {
        int index = 0;
        for (char c : original) {
            if (c == '\\') {
                newArray[index++] = '\\';
                newArray[index++] = '\\';
            } else if (c == '\n') {
                newArray[index++] = '\\';
                newArray[index++] = 'n';
            } else if (c == '\t') {
                newArray[index++] = '\\';
                newArray[index++] = 't';
            } else {
                newArray[index++] = c;
            }
        }
        return index;
    }

    public static int replaceBackslashTable3(char[] original, char[] newArray) {
        int newArrayLength = 0;
        for (char c : original) {
            // copy regardless into output; this allow the loop to be unrolled ;)
            newArray[newArrayLength] = c;
            byte b = silly_table3[c % 256];
            if (c < 256 && b != 0) {
                // we need to copy from the last known index to the current one, excluded
                newArray[newArrayLength] = '\\';
                newArray[newArrayLength + 1] = (char) b;
                newArrayLength += 2;
            } else {
                newArrayLength++;
            }
        }
        return newArrayLength;
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {


        @Param({"3", "50"})
        public int specialCharPercentage;

        @Param({"65536"})
        public int size;
        public char[] inputstring;
        public byte[] latinInputString;
        public char[] outputstring;
        public byte[] latinOutputString;


        @Setup(Level.Trial)
        public void setUp(BenchmarkParams params) {
            inputstring = new char[size];
            int outputLength = populateChars(params, (index, latinChar) -> inputstring[index] = (char) latinChar, size, specialCharPercentage);
            outputstring = new char[outputLength];
            latinInputString = new byte[size];
            for (int i = 0; i < size; i++) {
                latinInputString[i] = (byte) inputstring[i];
            }
            latinOutputString = new byte[outputLength + 7];
        }
    }

    // better be safe and keep it the same to have reproducible results
    private static final int SEED = 42;

    @FunctionalInterface
    private interface IntIntBiConsumer {
        void accept(int index, int latinChar);
    }

    /**
     * Populates the input string with latin characters and special characters, based on the specialCharsProbability:
     */
    private static int populateChars(BenchmarkParams params, IntIntBiConsumer latinCharProducer, int count,
                                     int specialCharsProbability) {
        byte[] specialChars = specialCharsFor(params);
        byte[] latinChars = latinCharsExcluding(specialChars);
        Random random = new Random(SEED);
        int outputLength = count;
        for (int i = 0; i < count; i++) {
            if (random.nextInt( 100) <= specialCharsProbability) {
                int specialCharIndex = random.nextInt(specialChars.length);
                latinCharProducer.accept(i, Byte.toUnsignedInt(specialChars[specialCharIndex]));
                outputLength++;
            } else {
                int latinCharIndex = random.nextInt(latinChars.length);
                latinCharProducer.accept(i, Byte.toUnsignedInt(latinChars[latinCharIndex]));
            }
        }
         return outputLength;
    }

    private static byte[] specialCharsFor(BenchmarkParams params) {
        final byte[] specialChars;
        if (params.getBenchmark().contains("1")) {
            specialChars = new byte[1];
            specialChars[0] = '\\';
        } else if (params.getBenchmark().contains("2")) {
            specialChars = new byte[2];
            specialChars[0] = '\\';
            specialChars[1] = '\n';
        } else if (params.getBenchmark().contains("3")) {
            specialChars = new byte[3];
            specialChars[0] = '\\';
            specialChars[1] = '\n';
            specialChars[2] = '\t';
        } else {
            throw new IllegalArgumentException("Unknown benchmark: " + params.getBenchmark());
        }
        return specialChars;
    }

    private static byte[] latinCharsExcluding(byte[] specialChars) {
        BitSet specialCharsSet = new BitSet(256);
        for (byte specialChar : specialChars) {
            specialCharsSet.set(specialChar);
        }
        BitSet latinChars = new BitSet(256);
        latinChars.set(0, 255);
        latinChars.andNot(specialCharsSet);
        byte[] nonSpecialLatinChars = new byte[latinChars.cardinality()];
        for (int i = latinChars.nextSetBit(0), j = 0; i >= 0; i = latinChars.nextSetBit(i + 1), j++) {
            nonSpecialLatinChars[j] = (byte) i;
        }
        return nonSpecialLatinChars;
    }

    private static final VarHandle LONG_COMPRESS_WRITER = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_READER = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);


    public static long transformMSBSetIntoFF(long input) {
        // keep only MSB: TODO needed here?
        // long mask = 0x8080808080808080L;
        // long isolated = input & mask;
        return ((input >>> 7) * 0xFF);
    }

    // it set the MSB of the zero bytes, zero otherwise
    private static long setMSBonZeroBytes(long word) {
        long tmp = (word & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL;
        // this is necessary since we can have negative ones, which already set the MSB (!)
        // TODO verify if it really is needed!
        tmp = ~(tmp | word | 0x7F7F7F7F7F7F7F7FL);
        return tmp;
    }

    private static long ffNotZeroBytes(long input) {
        return transformMSBSetIntoFF(setMSBonZeroBytes(input) ^ 0x8080808080808080L);
    }


    public static void main(String[] args) {
        final byte[] input  = new byte[] { 'a', 'b', '\n', '\\'};
        final byte[] output = new byte[input.length * 2];
        replaceBackslashRawCompressedTable3(input, output);
    }

    public static int replaceBackslashRawCompressedTable3(byte[] original, byte[] newArray) {
        int newArrayLength = 0;
        int fourCharsBatches = original.length / 4;
        for (int b = 0; b < fourCharsBatches; b++) {
            int i = b * 4;
            int readChars = (int) INT_READER.get(original, i);
            long latinChars = Long.expand(Integer.toUnsignedLong(readChars), 0x00FF_00FF_00FF_00FFL);
            // it will be zero if it's a
            byte b0 = silly_table3[(int) (latinChars & 0xFF)];
            // place this near to the latinChars it refer to
            latinChars |= (long) b0 << 8;
            byte b1 = silly_table3[(int) ((latinChars >>> 16) & 0xFF)];
            latinChars |= (long) b1 << 24;
            byte b2 = silly_table3[(int) ((latinChars >>> 32) & 0xFF)];
            latinChars |= (long) b2 << 40;
            byte b3 = silly_table3[(int) ((latinChars >>> 48) & 0xFF)];
            latinChars |= (long) b3 << 56;
            // now we have R replacements chars, near to the originals S
            // i.e. 0xRRSS_RRSS_RRSS_RRSS
            // R == 0 -> keep       0x00SS  -> latinChars is already OK!
            // R != 0 -> replace    0x00SS  with 0xRR92 -> latinChars need fixing!
            // we are not interested into the replacement chars here - filter it out at the end
            // then move it to the right position to apply this to the original chars
            int digits = replaceChars(newArray, latinChars, newArrayLength);
            newArrayLength += digits;
        }
        int tail = original.length % 4;
        if (tail > 0) {
            long latinChars = 0;
            int idx = fourCharsBatches * 4;
            byte b0 = silly_table3[(original[idx] & 0xFF)];
            // place this near to the latinChars it refer to
            latinChars |= (long) b0 << 8;
            if (tail > 1) {
                byte b1 = silly_table3[(original[idx + 1] & 0xFF)];
                latinChars |= (long) b1 << 24;
                if (tail > 2) {
                    byte b2 = silly_table3[(original[idx + 2] & 0xFF)];
                    latinChars |= (long) b2 << 40;
                }
            }
            int digits = replaceChars(newArray, latinChars, newArrayLength);
            newArrayLength += digits;
        }
        return newArrayLength;
    }

    private static int replaceChars(byte[] newArray, long latinChars, int newArrayLength) {
        long ffIfNotZero = ((ffNotZeroBytes(latinChars) & 0xFF00_FF00_FF00_FF00L) >>> 8) | 0xFF00_FF00_FF00_FF00L;
        // the last 0xFF00_FF00_FF00_FF00L is needed to make sure that the replacement chars are left untouched
        // we now want to make sure that, if the replacement is needed, each 0xRRSS is replaced by 0xRR92
        long replacedChars = (((~ffIfNotZero & (latinChars & 0x00FF_00FF_00FF_00FFL)) |
              ffIfNotZero & 0x005c_005c_005c_005cL) | (latinChars & 0xFF00_FF00_FF00_FF00L));
        long compressedChars = Long.compress(replacedChars, (ffIfNotZero << 8) | 0x00FF_00FF_00FF_00FFL);
        LONG_COMPRESS_WRITER.set(newArray, newArrayLength, compressedChars);
        int digits = Long.bitCount(ffIfNotZero) / 8;
        return digits;
    }

    private static final VarHandle SHORT_WRITER = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

    private static int writeToOutput(byte[] newArray, byte c, int newArrayLength) {
        int ch0 = c & 0xFF;
        byte b0 = silly_table3[ch0];
        int zeroIfEqualsZeroOrMinusOneIfNot = ((b0 | -b0) >> 31);
        // branch-less assign \\ if b != 0 or c if b == 0
        int firstChar = ((~zeroIfEqualsZeroOrMinusOneIfNot & ch0) | (zeroIfEqualsZeroOrMinusOneIfNot & '\\'));
        short twoChars = (short) ((b0 << 8) | firstChar);
        SHORT_WRITER.set(newArray, newArrayLength, twoChars);
        // branch-less increment by 2 if b != 0 or 1 if b == 0
        newArrayLength += 1 + (zeroIfEqualsZeroOrMinusOneIfNot & 1);
        return newArrayLength;
    }

    @Benchmark
    public void benchReplaceBackslash1(Blackhole blackhole, BenchmarkState state) {
        blackhole.consume(replaceBackslash1(state.inputstring, state.outputstring));
    }

    @Benchmark
    public void benchReplaceBackslashTable1(Blackhole blackhole, BenchmarkState state) {
        blackhole.consume(replaceBackslashTable1(state.inputstring, state.outputstring));
    }
    @Benchmark
    public void benchReplaceBackslash2(Blackhole blackhole, BenchmarkState state) {
        blackhole.consume(replaceBackslash2(state.inputstring, state.outputstring));
    }

    @Benchmark
    public void benchReplaceBackslashTable2(Blackhole blackhole, BenchmarkState state) {
        blackhole.consume(replaceBackslashTable2(state.inputstring, state.outputstring));
    }

    @Benchmark
    public void benchReplaceBackslash3(Blackhole blackhole, BenchmarkState state) {
        blackhole.consume(replaceBackslash3(state.inputstring, state.outputstring));
    }

    @Benchmark
    public void benchReplaceBackslashTable3(Blackhole blackhole, BenchmarkState state) {
        blackhole.consume(replaceBackslashTable3(state.inputstring, state.outputstring));
    }

    @Benchmark
    public void benchReplaceBackslashRawCompressedTable3(Blackhole blackhole, BenchmarkState state) {
        blackhole.consume(replaceBackslashRawCompressedTable3(state.latinInputString, state.latinOutputString));
    }

}
