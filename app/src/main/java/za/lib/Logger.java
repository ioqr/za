package za.lib;

import java.io.PrintStream;
import java.time.Instant;

// TODO make logger thread safe
public interface Logger {
    default void info(String fmt, Object... args) {}
    default void warn(String fmt, Object... args) {}
    default void error(String fmt, Object... args) {}
    default void debug(String fmt, Object... args) {}
    
    static Logger verbose(Class<?> clazz) {
        return verbose(clazz, System.out, System.err);
    }
    
    static Logger verbose(Class<?> clazz, PrintStream out, PrintStream err) {
        return new Logger() {
            @Override
            public void info(String fmt, Object... args) {
                out.println(format("INFO", fmt, args));
            }
            
            @Override
            public void warn(String fmt, Object... args) {
                out.println(format("WARN", fmt, args));
            }
            
            @Override
            public void error(String fmt, Object... args) {
                err.println(format("ERROR", fmt, args));
            }
            
            @Override
            public void debug(String fmt, Object... args) {
                out.println(format("DEBUG", fmt, args));
            }
            
            private String format(String prefix, String fmt, Object... args) {
                try {
                    var sb = new StringBuilder();
                    sb.append(prefix);
                    sb.append(" (");
                    sb.append(Thread.currentThread().getId());
                    sb.append(") [");
                    sb.append(clazz.getSimpleName());
                    sb.append(" at ");
                    sb.append(Instant.now());
                    sb.append("] ");
                    sb.append(String.format(fmt, args));
                    return sb.toString();
                } catch (Exception e) {
                    e.printStackTrace();
                    // do not evaluate args, since this exception could be thrown by toString() calls, or
                    // from user payload in the format field ie "log.info(userDataContainingPercentSigns)"
                    return "Exception thrown in log formatting; level=" + prefix + "; fmt=" + fmt;
                }
            }
        };
    }
    
    static Logger silent() {
        return new Logger() {};
    }
}
