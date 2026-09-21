package sage;
import java.io.IOException;
public interface DVDStreamTransformProvider {
 String getTransportId();
 String getOutputFormat();
 boolean isAvailable();
 DVDStreamTransform open(DVDStreamTransformRequest request) throws IOException;
}
