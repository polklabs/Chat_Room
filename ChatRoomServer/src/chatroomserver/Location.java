/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatroomserver;

/**
 *
 * @author polka
 */
public class Location {
    public double lat;
    public double lon;
    public double el;
    
    public String name;
    
    public Location(double lat, double lon, double el, String name){
        this.lat = lat;
        this.lon = lon;
        this.el = el;
        this.name = name;
    }
    
    public Location(double lat, double lon, double el){
        this.lat = lat;
        this.lon = lon;
        this.el = el;
        this.name = "temp";
    }
    
    public static double distance(Location loc1, Location loc2) {

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(loc2.lat - loc1.lat);
        double lonDistance = Math.toRadians(loc2.lon - loc1.lon);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(loc1.lat)) * Math.cos(Math.toRadians(loc2.lat))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = loc1.el - loc2.el;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }
}
