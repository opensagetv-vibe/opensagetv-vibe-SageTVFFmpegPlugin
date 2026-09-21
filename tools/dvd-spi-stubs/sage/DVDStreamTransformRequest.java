package sage;
public final class DVDStreamTransformRequest {
 private final String transportId;
 private final String videoBitrate;
 public DVDStreamTransformRequest(String transportId,String videoBitrate) {
  this.transportId=transportId;
  this.videoBitrate=videoBitrate;
 }
 public String getTransportId() { return transportId; }
 public String getVideoBitrate() { return videoBitrate; }
}
