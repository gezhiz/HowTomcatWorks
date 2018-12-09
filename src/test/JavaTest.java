package test;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Vector;

/**
 * Created by gezz on 2018/12/7.
 */
public class JavaTest {
    @Test
    public void testList() {
        ArrayList arrayList = new ArrayList();
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        System.out.println(arrayList);

        Vector vector = new Vector();
        vector.add(null);
        vector.add(null);
        vector.add(null);
        System.out.println(vector);
    }
}
