package fr.sakura.bot.utils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.Set;

public final class StaffUtils {

    private static final Set<String> STAFF_ROLE_KEYWORDS = Set.of("support", "staff", "mod");
    private static final Set<Permission> STAFF_PERMISSIONS = Set.of(
            Permission.ADMINISTRATOR,
            Permission.MANAGE_SERVER,
            Permission.MESSAGE_MANAGE,
            Permission.MANAGE_CHANNEL,
            Permission.MODERATE_MEMBERS
    );

    private StaffUtils() {
        // Utility class
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static boolean isStaff(Member member) {
        if (member == null) {
            return false;
        }

        if (STAFF_PERMISSIONS.stream().anyMatch(member::hasPermission)) {
            return true;
        }

        return member.getRoles().stream()
                .map(Role::getName)
                .map(String::toLowerCase)
                .anyMatch(StaffUtils::containsStaffKeyword);
    }

    private static boolean containsStaffKeyword(String roleName) {
        for (String keyword : STAFF_ROLE_KEYWORDS) {
            if (roleName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
