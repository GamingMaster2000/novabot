package pokemon;

import core.Spawn;
import core.Util;
import maps.GeofenceIdentifier;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;

import java.awt.*;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static maps.Geofencing.getGeofence;

public class PokeSpawn extends Spawn
{
    private static final DecimalFormat df;

    static {
        (df = new DecimalFormat("#.##")).setRoundingMode(RoundingMode.CEILING);

    }

    public Instant disappearTime;
    public String form;
    public int id;
    public float iv;
    public int cp;
    public int level;
    private float weight;
    private float height;
    private int gender;
    private String suburb;
    private int iv_attack;
    private int iv_defense;
    private int iv_stamina;


    public PokeSpawn(int i) {
        super();
        this.id = i;
    }


    public PokeSpawn(final int id, final double lat, final double lon, final Instant disappearTime, final int attack, final int defense, final int stamina, final String move1, final String move2, final float weight, final float height, final int gender, final int form, int cp, double cpModifier) {
        super();
        this.disappearTime = null;
        this.form = null;
        this.suburb = null;
        this.disappearTime = disappearTime;
        properties.put("24h_time", getDespawnTime(printFormat24hr));
        properties.put("12h_time", getDespawnTime(printFormat12hr));
        properties.put("time_left",timeLeft());

        this.id = id;
        properties.put("pkmn_id", String.valueOf(id));

        String name = Util.capitaliseFirst(Pokemon.idToName(this.id));
        if (name.startsWith("Unown")) {
            name = "Unown";
        }

        properties.put("pkmn",name);

        this.lat = lat;
        properties.put("lat", String.valueOf(lat));

        this.lon = lon;
        properties.put("lng", String.valueOf(lon));

        if (novaBot.config.suburbsEnabled()) {
            novaBot.reverseGeocoder.geocodedLocation(lat, lon).getProperties().forEach(properties::put);
        }

        this.geofenceIdentifiers = getGeofence(lat,lon);

        properties.put("geofence", GeofenceIdentifier.listToString(geofenceIdentifiers));

        properties.put("gmaps",getGmapsLink());

        properties.put("applemaps",getAppleMapsLink());

        this.iv_attack = attack;
        properties.put("atk", String.valueOf(iv_attack));

        this.iv_defense = defense;
        properties.put("def", String.valueOf(iv_defense));

        this.iv_stamina = stamina;
        properties.put("sta", String.valueOf(iv_stamina));

        this.iv = (attack + defense + stamina) / 45.0f * 100.0f;
        properties.put("iv", getIv());

        this.move_1 = ((move1 == null) ? "unkn" : move1);
        properties.put("quick_move", move_1);

        this.move_2 = ((move2 == null) ? "unkn" : move2);
        properties.put("charge_move", move_2);

        this.weight = weight;
        properties.put("weight", getWeight());

        this.height = height;
        properties.put("height", getHeight());

        properties.put("size",getSize());

        this.gender = gender;
        properties.put("gender", getGender());

        if (form != 0 && id == 201) {
            this.id = id * 10 + form;
        }
        this.form = ((Pokemon.intToForm(form) == null) ? null : String.valueOf(Pokemon.intToForm(form)));
        properties.put("form", (this.form == null ? "" : this.form));

        this.cp = cp;
        properties.put("cp", cp == 0 ? "?" : String.valueOf(cp));

        properties.put("lvl30cp", cp == 0 ? "?" : String.valueOf(Pokemon.maxCpAtLevel(id, 30)));
        properties.put("lvl35cp", cp == 0 ? "?" : String.valueOf(Pokemon.maxCpAtLevel(id, 35)));

        level = Pokemon.getLevel(cpModifier);
        properties.put("level", String.valueOf(level));
    }

    public Message buildMessage(String formatFile) {
        if(builtMessages.get(formatFile) == null) {

            if (!properties.containsKey("city")) {
                novaBot.reverseGeocoder.geocodedLocation(lat, lon).getProperties().forEach(properties::put);
            }

            final MessageBuilder messageBuilder = new MessageBuilder();
            final EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setColor(getColor());
            embedBuilder.setTitle(novaBot.config.formatStr(properties, (encountered()) ? novaBot.config.getEncounterTitleFormatting(formatFile) : (novaBot.config.getTitleFormatting(formatFile, "pokemon"))), novaBot.config.formatStr(properties, novaBot.config.getTitleUrl(formatFile, "pokemon")));
            embedBuilder.setDescription(novaBot.config.formatStr(properties, (encountered()) ? novaBot.config.getEncounterBodyFormatting(formatFile) : novaBot.config.getBodyFormatting(formatFile, "pokemon")));
            embedBuilder.setThumbnail(Pokemon.getIcon(this.id));
            if (novaBot.config.showMap(formatFile, "pokemon")) {
                embedBuilder.setImage(this.getImage(formatFile));
            }
            embedBuilder.setFooter(novaBot.config.getFooterText(), null);
            embedBuilder.setTimestamp(Instant.now());
            messageBuilder.setEmbed(embedBuilder.build());

            String contentFormatting = novaBot.config.getContentFormatting(formatFile, formatKey);
            if (contentFormatting != null && !contentFormatting.isEmpty()) {
                messageBuilder.append(novaBot.config.formatStr(properties, novaBot.config.getContentFormatting(formatFile, formatKey)));
            }

            builtMessages.put(formatFile,messageBuilder.build());
        }

        return builtMessages.get(formatFile);
    }

    public int getFilterId() {
        if (id >= 2011) {
            return 201;
        } else {
            return id;
        }
    }

    @Override
    public int hashCode() {
        int hash = (int) (lat * lon);

        hash *= novaBot.suburbs.indexOf(suburb);

        hash *= id;

        hash += iv * 1000;

        hash *= gender + 1;

        hash += (weight * height);

        hash += PokeMove.indexOf(move_1) * PokeMove.indexOf(move_2);

        hash += (iv_attack + iv_defense + iv_stamina);

        hash += cp;

        hash += disappearTime.hashCode();

        return hash;
    }

    @Override
    public String toString() {
        if (this.disappearTime == null) {
            return "[" + ((this.suburb == null) ? "null" : this.suburb) + "]" + Pokemon.idToName(this.id) + " " + PokeSpawn.df.format(this.iv) + "%, " + this.move_1 + ", " + this.move_2;
        }
        return "[" + ((this.suburb == null) ? "null" : this.suburb) + "]" + Pokemon.idToName(this.id) + " " + PokeSpawn.df.format(this.iv) + "%,CP: " + this.cp + ", " + this.move_1 + ", " + this.move_2 + ", for " + this.timeLeft() + ", disappears at " + this.disappearTime;
    }

//    public static void main(final String[] args) {
//
//        testing = true;
//        loadConfig();
//        loadGeofences();
//
////        DBManager.novabotdbConnect();
//
////        PokeSpawn spawn = new PokeSpawn(149, -35.214385, 149.0405493, new Timestamp(DBManager.getCurrentTime().getTime() + 6000), 1, 1, 1, "Dragon Tail", "Outrage", 13.0f, 13.0f, 3, 0, 2142,0.743 );
//        PokeSpawn spawn = new PokeSpawn(149,
//                -35.214385,
//                149.0405493,
//                Util.getCurrentTime(ZoneId.of("UTC")).toInstant().plusSeconds(300),
//                0,
//                0,
//                0,
//                "unkn",
//                "unkn",
//                0,
//                0,
//                0,
//                0,
//                0,
//                0);
//
//        Instant now = Instant.now() ;                       // Simulating fetching a `Timestamp` from database by using current moment in UTC.
////        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Adelaide"));
//        ZoneId     zoneIdDefault = ZoneId.systemDefault() ;
//        ZoneOffset zoneOffset    = zoneIdDefault.getRules().getOffset(now) ;
//
//        java.sql.Timestamp ts = java.sql.Timestamp.from(now) ;  // Actually in UTC, but it's `toString` method applies JVM’s current default time zone while generating string.
//        Instant instant = ts.toInstant() ;                  // Same moment, also in UTC.
//        ZoneId z = ZoneId.of( "Australia/Adelaide" ) ;        // Or call your global var: `Globals.LOCALZONEID`.
//        ZonedDateTime zdt = instant.atZone( z );            // Same moment, same point on timeline, but with wall-clock time seen in a particular zone.
//
//        System.out.println("Original instant: " + now);
//        System.out.println("instant to timestamp: " + ts);
//        System.out.println("Timestamp to instant: " +instant.toString());
//        System.out.println("ZonedDateTime: " +zdt);
//
//        System.out.println("Current adelaide time: " + printFormat24hr.format(Util.getCurrentTime(config.getTimeZone())));
//        System.out.println("UTC disappear time " + spawn.disappearTime);
//        System.out.println("24h_time in " + config.getTimeZone() + " " + spawn.properties.get("24h_time"));
//        System.out.println("12h_time in " + config.getTimeZone() + " " + spawn.properties.get("12h_time"));
//        System.out.println("time_left: " + spawn.properties.get("time_left"));
////        System.out.println(config.formatStr(spawn.properties,config.getTitleFormatting()));
//        if(spawn.encountered()){
//            System.out.println(config.formatStr(spawn.properties,config.getEncounterBodyFormatting()));
//        }else {
//            System.out.println(config.formatStr(spawn.properties, config.getBodyFormatting()));
//        }
//        System.out.println(new PokeSpawn(12, -35.0, 149.0, new Time(1L), 1, 1, 1, "", "", 13.0f, 13.0f, 3, 1, 2142,0.743 ).hashCode());
//        System.out.println(new PokeSpawn(12, -35.0, 149.0, new Time(214L), 1, 1, 1, "", "", 13.0f, 13.0f, 3, 1, 2142,.743 ).hashCode());
//    }

    private boolean encountered() {
        return iv != 0 || !move_1.equals("unkn") || !move_2.equals("unkn") || cp > 0;
    }

    private Color getColor() {
        if(iv < 25)
            return new Color(0x9d9d9d);
        if(iv < 50)
            return new Color(0xffffff);
        if(iv < 81)
            return new Color(0x1eff00);
        if(iv < 90)
            return new Color(0x0070dd);
        if(iv < 100)
            return new Color(0xa335ee);
        if(iv == 100)
            return new Color(0xff8000);

        return Color.GRAY;
    }

    private String getDespawnTime(DateTimeFormatter printFormat) {
        return printFormat.format(ZonedDateTime.ofInstant(disappearTime, novaBot.config.getTimeZone()));
    }

    private String getGender() {
        if (this.gender == 1) {
            return "\u2642";
        }
        if (this.gender == 2) {
            return "\u2640";
        }
        if (this.gender == 3) {
            return "\u26b2";
        }
        return "?";
    }

    private String getHeight() {
        return PokeSpawn.df.format(this.height);
    }

    private String getIv() {
        return PokeSpawn.df.format(this.iv);
    }

    private String getSize() {
        if ((weight == 0) && (height == 0)) {
            return "?";
        }

        return Pokemon.getSize(id,height,weight);
    }

    private String getWeight() {
        return PokeSpawn.df.format(this.weight);
    }

    private String timeLeft() {
        long diff = Duration.between(Instant.now(), disappearTime).toMillis();

        String time = String.format("%02dm %02ds",
                                    MILLISECONDS.toMinutes(Math.abs(diff)),
                                    MILLISECONDS.toSeconds(Math.abs(diff)) -
                                    (MILLISECONDS.toMinutes(Math.abs(diff)) * 60)
                                   );

        if (diff < 0) {
            time = "-" + time;
        }

        return time;
    }
}
