package za.engine;

import za.lib.Plugin;

/**
 * Za main class
 * 
 * Usage:
 * java -jar za.jar <plugin jar files> <engine arguments>
 *
 * Example: 
 * java -jar za.jar plugin.jar plugin2.jar plugin3.jar --http-threads 8 --rmq-user ioqr
 */
public final class Main {
    private Main() {}
    public static void main(String[] args) {
        var za = Engine.create(args, new Plugin[0]);  // TODO load jar files
        za.start();
    }
}
