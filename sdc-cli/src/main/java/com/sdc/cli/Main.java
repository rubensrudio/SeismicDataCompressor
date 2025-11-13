package com.sdc.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import com.sdc.ai.AeRuntime;
import com.sdc.core.SdcFileReader;
import com.sdc.core.SdcHeader;

import java.nio.file.Files;
import java.nio.file.Path;

@Command(name = "sdc", mixinStandardHelpOptions = true, version = "0.1.0",
         description = "AI-Enhanced Seismic Data Compressor CLI (prototype)")
public class Main implements Runnable {

    @Option(names = {"-i","--input"}, description = "Arquivo de entrada (.sdc para --inspect)", required = false)
    private Path input;

    @Option(names = {"-o","--output"}, description = "Arquivo de saída (.sdc)", required = false)
    private Path output;

    @Option(names = {"--inspect"}, description = "Inspeciona metadados/estrutura de um arquivo .sdc", required = false)
    private boolean inspect;

    public void run() {
        AeRuntime rt = new AeRuntime();
        System.out.println("[SDC] Runtime OK. TensorFlow version: " + rt.tfVersion());

        if (inspect) {
            if (input == null) {
                System.err.println("[SDC] --inspect requer --input apontando para um arquivo .sdc");
                return;
            }
            inspectSdc(input);
        } else {
            System.out.println("[SDC] Nenhuma ação específica informada. Use --help para ver opções.");
        }
    }

    private void inspectSdc(Path path) {
        try {
            if (!Files.exists(path)) {
                System.err.println("[SDC] Arquivo não encontrado: " + path);
                return;
            }
            SdcHeader header = SdcFileReader.readHeader(path);
            System.out.println("[SDC] Arquivo: " + path);
            System.out.println("[SDC] Header: " + header);
        } catch (Exception e) {
            System.err.println("[SDC] Falha ao ler arquivo .sdc: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new Main()).execute(args);
        System.exit(exit);
    }
}
