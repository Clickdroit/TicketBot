package fr.sakura.bot.utils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffUtils {

    private static volatile Set<String> staffRoleKeywords = Set.of("support", "staff", "mod");
    private static volatile Set<Permission> staffPermissions = Set.of(
            Permission.ADMINISTRATOR,
            Permission.MANAGE_SERVER,
            Permission.MESSAGE_MANAGE,
            Permission.MANAGE_CHANNEL,
            Permission.MODERATE_MEMBERS
    );

    private StaffUtils() {
    }

    public static boolean isStaff(Member member) {
        if (member == null) {
            return false;
        }
        if (staffPermissions.stream().anyMatch(member::hasPermission)) {
            return true;
        }
        return member.getRoles().stream().map(Role::getName).map(String::toLowerCase).anyMatch(StaffUtils::containsStaffKeyword);
    }

    public static void configureRoleKeywords(Collection<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return;
        }
        Set<String> normalized = ConcurrentHashMap.newKeySet();
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()) {
                normalized.add(keyword.toLowerCase());
            }
        }
        if (!normalized.isEmpty()) {
            staffRoleKeywords = Set.copyOf(normalized);
        }
    }

    public static void configurePermissions(Collection<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        Set<Permission> normalized = ConcurrentHashMap.newKeySet();
        for (Permission permission : permissions) {
            if (permission != null) {
                normalized.add(permission);
            }
        }
        if (!normalized.isEmpty()) {
            staffPermissions = Set.copyOf(normalized);
        }
    }

    private static boolean containsStaffKeyword(String roleName) {
        for (String keyword : staffRoleKeywords) {
            if (roleName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
