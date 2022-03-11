package za.lib;

public interface Scraper {
    /**
     * Perform web scraping on a set of input parameters
     * 
     * Note:
     * Scraper does not return outputted data. Plugins keep control of data since there
     * is unknown data requirements per plugin, so the engine would get in the way of too
     * many good plugins. To output data, directly pass or wrap the plugin's out() method
     * 
     *      var myCallback = (data) -> {
     *          plugin.out(serialize(data))
     *      }
     * 
     *      plugin.subscribe("channel.name", scraper::scrape)
     * 
     * @param input
     */
    void scrape(Message input); 
}
