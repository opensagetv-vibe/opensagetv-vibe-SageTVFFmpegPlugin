package sage;
public interface SageTVPlugin extends SageTVEventListener {
 int CONFIG_BOOL=1, CONFIG_INTEGER=2, CONFIG_TEXT=3, CONFIG_CHOICE=4, CONFIG_MULTICHOICE=5, CONFIG_FILE=6, CONFIG_DIRECTORY=7, CONFIG_BUTTON=8, CONFIG_PASSWORD=9;
 void start(); void stop(); void destroy(); String[] getConfigSettings(); String getConfigValue(String setting); String[] getConfigValues(String setting); int getConfigType(String setting); void setConfigValue(String setting,String value); void setConfigValues(String setting,String[] values); String[] getConfigOptions(String setting); String getConfigHelpText(String setting); String getConfigLabel(String setting); void resetConfig();
}
