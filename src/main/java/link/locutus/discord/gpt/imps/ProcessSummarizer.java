package link.locutus.discord.gpt.imps;

import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.ISummarizer;
import link.locutus.discord.util.StringMan;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ProcessSummarizer implements ISummarizer {
    private final File file;
    private final String prompt;
    private final int promptTokens;
    private final ModelType model;
    private final File venvExe;

    public ProcessSummarizer(File venvExe, File file) {
        this.venvExe = venvExe;
        this.file = file;
        this.prompt = """
                Write a concise summary which preserves syntax, equations, arguments and constraints of the following:
                
                {query}
                
                Concise summary:""";
        this.model = ModelType.GPT_4;
        this.promptTokens = GPTUtil.getTokens(prompt.replace("{query}", ""), model);
    }
    @Override
    public String summarize(String text) {
        int cap = 4096 - 4;
        int remaining = cap - promptTokens;
        List<String> summaries = new ArrayList<>();
        for (String chunk : GPTUtil.getChunks(text, model, remaining)) {
            String result = summarizeChunk(chunk);
            summaries.add(result);
        }
        return String.join("\n", summaries);
    }

    public String summarizeChunk(String chunk) {
        String full = prompt.replace("{query}", chunk);

        String encodedString = Base64.getEncoder().encodeToString(full.getBytes());
        List<String> lines = new ArrayList<>();
        String command = venvExe == null ? "python" : venvExe.getAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(command, file.getAbsolutePath(), encodedString).redirectErrorStream(true);
        try {
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String result = StringMan.join(lines, "\n");
        if (result.contains("result:")) {
            result = result.substring(result.indexOf("result:") + 7);
            return result;
        } else {
            System.err.println(result);
            throw new IllegalArgumentException("Unknown process result (see console)");
        }
    }
}
