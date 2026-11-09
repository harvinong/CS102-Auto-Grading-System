/*
 * Name:
 * Email ID:
 */

import java.util.*;   

public class ShapeComparators implements Comparator<Shape>{

    public int compare(Shape o1, Shape o2) {
        int value = Double.compare(o2.getArea(), o1.getArea());

        if (value != 0){
            return value;
        }
        return Double.compare(o2.getPerimeter(), o1.getPerimeter());
    }
}
