package com.myyak.service.serverLogService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

@Service
public class ServerLogServiceImpl implements ServerLogService {
    private static final ZoneId LOG_ZONE = ZoneId.of("Asia/Seoul");
    private static final long MAX_READ_CHARS = 16 * 1024 * 1024;
    private static final int MAX_LINE_CHARS = 64 * 1024;
    private final Path logFile;
    private final Clock clock;

    @Autowired
    public ServerLogServiceImpl(
            @Value("${logging.file.name:${user.home}/.myyak/logs/application.log}") String logFile) {
        this(Path.of(logFile), Clock.systemUTC());
    }

    ServerLogServiceImpl(Path logFile, Clock clock) {
        this.logFile = logFile.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public void readLines(int hours, Consumer<String> consumer) {
        if (hours < 1 || hours > 168) throw new GeneralException(ErrorStatus._BAD_REQUEST);
        LocalDate firstDay = clock.instant().minusSeconds(hours * 3600L).atZone(LOG_ZONE).toLocalDate();
        Pattern archiveName = Pattern.compile(Pattern.quote(logFile.getFileName().toString())
                + "\\.(\\d{4}-\\d{2}-\\d{2})\\.(\\d+)\\.gz");
        try {
            if (!Files.isRegularFile(logFile, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing log file");
            var archives = new ArrayList<Path>();
            try (var files = Files.list(logFile.getParent())) {
                files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)).forEach(file -> {
                    var match = archiveName.matcher(file.getFileName().toString());
                    if (match.matches() && match.group(1).compareTo(firstDay.toString()) >= 0) archives.add(file);
                });
            }
            archives.sort(Comparator.comparing((Path path) -> {
                var match = archiveName.matcher(path.getFileName().toString());
                match.matches();
                return match.group(1);
            }).thenComparingInt(path -> {
                var match = archiveName.matcher(path.getFileName().toString());
                match.matches();
                return Integer.parseInt(match.group(2));
            }));
            archives.add(logFile);
            long[] readChars = {0};
            for (Path file : archives) {
                try (InputStream input = Files.newInputStream(file);
                     Reader reader = new InputStreamReader(file.equals(logFile) ? input : new GZIPInputStream(input), StandardCharsets.UTF_8)) {
                    readFile(reader, readChars, consumer);
                }
            }
        } catch (IOException | SecurityException exception) {
            // Do not expose file paths or recursively log a failing log-query request.
            throw new GeneralException(ErrorStatus.ADMIN_LOG_UNAVAILABLE);
        }
    }

    private void readFile(Reader reader, long[] readChars, Consumer<String> consumer) throws IOException {
        char[] buffer = new char[8192];
        StringBuilder line = new StringBuilder();
        int length;
        while ((length = reader.read(buffer)) != -1) {
            readChars[0] += length;
            if (readChars[0] > MAX_READ_CHARS) throw new GeneralException(ErrorStatus.ADMIN_LOG_RANGE_TOO_LARGE);
            for (int index = 0; index < length; index++) {
                char character = buffer[index];
                if (character == '\n') {
                    consumer.accept(line.toString());
                    line.setLength(0);
                } else if (character != '\r') {
                    if (line.length() >= MAX_LINE_CHARS) throw new GeneralException(ErrorStatus.ADMIN_LOG_RANGE_TOO_LARGE);
                    line.append(character);
                }
            }
        }
        if (!line.isEmpty()) consumer.accept(line.toString());
    }
}
