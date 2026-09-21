package sage;
import java.io.Closeable;
import java.io.IOException;
public interface DVDStreamTransform extends Closeable {
 void write(byte[] data,int offset,int length) throws IOException;
 byte[] pollOutput(long timeoutMillis) throws IOException;
 boolean isOutputEnded();
 void closeInput();
 void close();
}
