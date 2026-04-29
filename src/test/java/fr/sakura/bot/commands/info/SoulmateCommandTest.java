package fr.sakura.bot.commands.info;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

public class SoulmateCommandTest {

    @Test
    public void testCalculateSoulmateIsStatic() throws Exception {
        SoulmateCommand command = new SoulmateCommand();
        Method method = SoulmateCommand.class.getDeclaredMethod("calculateSoulmate", String.class, String.class);
        method.setAccessible(true);

        String id1 = "123456789";
        String id2 = "987654321";

        int result1 = (int) method.invoke(command, id1, id2);
        int result2 = (int) method.invoke(command, id1, id2);

        assertEquals(result1, result2, "Results should be static for the same IDs");
    }

    @Test
    public void testCalculateSoulmateIsCommutative() throws Exception {
        SoulmateCommand command = new SoulmateCommand();
        Method method = SoulmateCommand.class.getDeclaredMethod("calculateSoulmate", String.class, String.class);
        method.setAccessible(true);

        String id1 = "123456789";
        String id2 = "987654321";

        int result1 = (int) method.invoke(command, id1, id2);
        int result2 = (int) method.invoke(command, id2, id1);

        assertEquals(result1, result2, "Results should be commutative (order doesn't matter)");
    }

    @Test
    public void testCalculateSoulmateSpecialCase() throws Exception {
        SoulmateCommand command = new SoulmateCommand();
        Method method = SoulmateCommand.class.getDeclaredMethod("calculateSoulmate", String.class, String.class);
        method.setAccessible(true);

        String id1 = "838024514369617930";
        String id2 = "993896595554848829";

        int result = (int) method.invoke(command, id1, id2);

        assertEquals(98, result, "Special IDs should return 98%");
    }
}
