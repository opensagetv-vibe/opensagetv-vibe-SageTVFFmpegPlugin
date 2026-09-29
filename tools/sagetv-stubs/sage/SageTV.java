package sage;

/**
 * Compile-only CI stub for the public SageTV API dispatcher. Release builds
 * use an unmodified stock Sage.jar; this class is never packaged.
 */
public final class SageTV {
  private SageTV() { }

  public static Object api(String method, Object[] arguments) {
    return null;
  }
}
