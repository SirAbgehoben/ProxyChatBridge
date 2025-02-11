package org.abgehoben.proxyChatBridge;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
yes, I know this was fucking unnecessary but... I don't care
only took like 5 hours to write this.
*/

public class TextComponentParser {

    private static final Pattern TEXT_COMPONENT_PATTERN = Pattern.compile("TextComponentImpl\\{content=\"(.*?)\", style=StyleImpl\\{(.+?)\\}, children=\\[(.*?)\\]\\}");
    private static final Pattern STYLE_PATTERN = Pattern.compile("(\\w+)=([^,]+)");
    private static final Pattern NAMED_TEXT_COLOR_PATTERN = Pattern.compile("NamedTextColor\\{name=\"(\\w+)\", value=\"#([0-9a-fA-F]{6})\"\\}");
    private static final Pattern MINECRAFT_COLOR_CODE_PATTERN = Pattern.compile("§([0-9a-fl-or])");

    public static String parseToDiscordFormat(String input) {
        return "```ansi\n" + parseComponent(input) + "\u001B[0m```";
    }

    private static String parseComponent(String componentString) {
        Matcher componentMatcher = TEXT_COMPONENT_PATTERN.matcher(componentString);
        StringBuilder discordString = new StringBuilder();

        if (componentMatcher.matches()) {
            String content = componentMatcher.group(1);
            String styleString = componentMatcher.group(2);
            String childrenString = componentMatcher.group(3);

            String ansiCode = getAnsiCode(styleString);

            discordString.append(ansiCode);
            discordString.append(handleMinecraftColorCodes(content));

            if (!childrenString.isEmpty()) {
                String[] children = childrenString.split(", TextComponentImpl\\{");
                for (int i = 0; i < children.length; i++) {
                    if (i > 0) {
                        children[i] = "TextComponentImpl{" + children[i];
                    }
                    discordString.append(parseComponent(children[i]));
                }
            }
            discordString.append("\u001B[0m"); // Add reset after each component

        } else {
            return handleMinecraftColorCodes(componentString);
        }

        return discordString.toString();
    }

    private static String getAnsiCode(String styleString) {
        Matcher styleMatcher = STYLE_PATTERN.matcher(styleString);
        String colorCode = "";
        String formatCode = "";

        while (styleMatcher.find()) {
            String key = styleMatcher.group(1);
            String value = styleMatcher.group(2);

            if (key.equals("color")) {
                Matcher colorMatcher = NAMED_TEXT_COLOR_PATTERN.matcher(value);
                if (colorMatcher.matches()) {
                    String colorName = colorMatcher.group(1);
                    colorCode = getColorAnsiCode(colorName);
                }
            } else if (key.equals("bold") && value.equals("TRUE")) {
                formatCode += "1;";
            } else if (key.equals("italic") && value.equals("TRUE")) {
                formatCode += "3;";
            } else if (key.equals("underlined") && value.equals("TRUE")) {
                formatCode += "4;";
            } else if (key.equals("strikethrough") && value.equals("TRUE")) {
                formatCode += "9;";
            }
        }

        return getCombinedAnsiCode(formatCode, colorCode);
    }

    private static String getColorAnsiCode(String colorName) {
        switch (colorName) {
            case "black":
                return "30";
            case "dark_blue":
                return "34";
            case "dark_green":
                return "32";
            case "dark_aqua":
                return "36";
            case "dark_red":
                return "31";
            case "dark_purple":
                return "35";
            case "gold":
                return "33";
            case "gray":
                return "37";
            case "dark_gray":
                return "90";
            case "blue":
                return "94";
            case "green":
                return "92";
            case "aqua":
                return "96";
            case "red":
                return "91";
            case "light_purple":
                return "95";
            case "yellow":
                return "93";
            case "white":
                return "97";
            default:
                return "";
        }
    }

    private static String handleMinecraftColorCodes(String text) {
        Matcher matcher = MINECRAFT_COLOR_CODE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String code = matcher.group(1);
            String ansiCode = minecraftColorCodeToAnsi(code);
            matcher.appendReplacement(sb, ansiCode);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private static String minecraftColorCodeToAnsi(String code) {
        switch (code) {
            case "0":
                return "\u001B[0;30m"; // black
            case "1":
                return "\u001B[0;34m"; // dark_blue
            case "2":
                return "\u001B[0;32m"; // dark_green
            case "3":
                return "\u001B[0;36m"; // dark_aqua
            case "4":
                return "\u001B[0;31m"; // dark_red
            case "5":
                return "\u001B[0;35m"; // dark_purple
            case "6":
                return "\u001B[0;33m"; // gold
            case "7":
                return "\u001B[0;37m"; // gray
            case "8":
                return "\u001B[0;90m"; // dark_gray
            case "9":
                return "\u001B[0;94m"; // blue
            case "a":
                return "\u001B[0;92m"; // green
            case "b":
                return "\u001B[0;96m"; // aqua
            case "c":
                return "\u001B[0;91m"; // red
            case "d":
                return "\u001B[0;95m"; // light_purple
            case "e":
                return "\u001B[0;93m"; // yellow
            case "f":
                return "\u001B[0;97m"; // white
            case "l":
                return "\u001B[1m";  // bold
            case "m":
                return "\u001B[9m";  // strikethrough
            case "n":
                return "\u001B[4m";  // underlined
            case "o":
                return "\u001B[3m";  // italic
            case "r":
                return "\u001B[0m";  // reset
            default:
                return "";
        }
    }

    private static String getCombinedAnsiCode(String formatCode, String colorCode) {
        String combinedCode = "";
        if (!formatCode.isEmpty()) {
            combinedCode += "\u001B[" + formatCode;
        }
        if (!colorCode.isEmpty()) {
            if (!combinedCode.isEmpty()) {
                combinedCode += ";";
            } else {
                combinedCode += "\u001B[";
            }
            combinedCode += colorCode;
        }
        if (!combinedCode.isEmpty()) {
            combinedCode += "m";
        }
        return combinedCode;
    }

    //

    static Color DARK_RED = new Color(139, 0, 0); // define a custom color constant
    static Color DARK_GREEN = new Color(0, 128, 0); // define a custom color constant
    static Color AQUA = new Color(0, 255, 255);
    static Color DARK_AQUA = new Color(0, 128, 128); // define a custom color constant
    static Color DARK_BLUE = new Color(0, 0, 128); // define a custom color constant
    static Color PURPLE = new Color(128, 0, 128);

    private static final Map<Color, String> colorToMinecraftCode = new HashMap<>();

    static {
        colorToMinecraftCode.put(Color.BLACK, "§0");
        colorToMinecraftCode.put(Color.DARK_GRAY, "§8");
        colorToMinecraftCode.put(Color.GRAY, "§7");
        colorToMinecraftCode.put(Color.WHITE, "§f");
        colorToMinecraftCode.put(Color.RED, "§c");
        colorToMinecraftCode.put(TextComponentParser.DARK_RED, "§4");
        colorToMinecraftCode.put(Color.ORANGE, "§6");
        colorToMinecraftCode.put(Color.YELLOW, "§e");
        colorToMinecraftCode.put(Color.GREEN, "§a");
        colorToMinecraftCode.put(TextComponentParser.DARK_GREEN, "§2");
        colorToMinecraftCode.put(TextComponentParser.AQUA, "§b");
        colorToMinecraftCode.put(TextComponentParser.DARK_AQUA, "§3");
        colorToMinecraftCode.put(Color.BLUE, "§9");
        colorToMinecraftCode.put(TextComponentParser.DARK_BLUE, "§1");
        colorToMinecraftCode.put(TextComponentParser.PURPLE, "§5");
        colorToMinecraftCode.put(Color.MAGENTA, "§d");
        colorToMinecraftCode.put(Color.PINK, "§d");
    }

    public static String getMinecraftColorCode(Color color) {
        /*
        if (color == null) {
            color = Color.white; //wait what?
        }
        */

        Color nearestColor = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Color mcColor : colorToMinecraftCode.keySet()) {
            double distance = getColorDistance(color, mcColor);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestColor = mcColor;
            }
        }

        return colorToMinecraftCode.getOrDefault(nearestColor, "§f");
    }

    public static double getColorDistance(Color c1, Color c2) {

        if (c1 == null || c2 == null) {
            Double o = null;
            return o;
        }

        int rDiff = c1.getRed() - c2.getRed();
        int gDiff = c1.getGreen() - c2.getGreen();
        int bDiff = c1.getBlue() - c2.getBlue();
        return Math.sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff);
    }

}
