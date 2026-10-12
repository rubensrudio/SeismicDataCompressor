package com.sdc.bench;

import com.sdc.core.SegyIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SyntheticSegyGeneratorTest {

    @TempDir
    Path tmp;

    @Test
    void tinyPresetIsBitExactAcrossRuns() throws Exception {
        SyntheticSegyGenerator.Spec spec = SyntheticSegyGenerator.TINY;
        Path a = tmp.resolve("a.sgy");
        Path b = tmp.resolve("b.sgy");
        SyntheticSegyGenerator.write(spec, a);
        SyntheticSegyGenerator.write(spec, b);

        assertEquals(spec.fileBytes(), Files.size(a));
        assertEquals(BenchmarkRunner.sha256(a), BenchmarkRunner.sha256(b));
        // hash canônico: garante que o dataset é o mesmo em qualquer JVM/CPU
        assertEquals(spec.expectedSha256(), BenchmarkRunner.sha256(a));
    }

    @Test
    void generatedFileIsReadableBySegyIO() throws Exception {
        SyntheticSegyGenerator.Spec spec = SyntheticSegyGenerator.TINY;
        Path f = tmp.resolve("tiny.sgy");
        SyntheticSegyGenerator.write(spec, f);

        SegyIO.SegyDataset ds = SegyIO.read(f);
        assertEquals(spec.traceCount(), ds.traceCount());
        assertEquals(spec.samples(), ds.samplesPerTrace);
        assertEquals(5, ds.sampleFormatCode);
        assertEquals(spec.inlines(), ds.traceGrid.inlineCount());
        assertEquals(spec.xlines(), ds.traceGrid.xlineCount());
        assertEquals(SyntheticSegyGenerator.FIRST_INLINE, ds.traceGrid.inlineValues[0]);
        assertEquals(SyntheticSegyGenerator.FIRST_XLINE, ds.traceGrid.xlineValues[0]);
    }

    @Test
    void byteShuffleRoundTrips() {
        byte[] in = new byte[4 * 1000];
        for (int i = 0; i < in.length; i++) in[i] = (byte) (i * 31 + 7);
        assertArrayEquals(in, BenchmarkRunner.unshuffle4(BenchmarkRunner.shuffle4(in)));
    }
}
