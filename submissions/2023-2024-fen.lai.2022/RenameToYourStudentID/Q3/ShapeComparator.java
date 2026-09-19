/*
 * Name: Fen Lai
 * Email ID: fen.lai.2022
 */

import java.util.*;   

public class ShapeComparator implements Comparator<Shape>{
  public int compare (Shape shape1, Shape shape2) {
    if (shape1.getArea() < shape2.getArea()) {
      return -1;
    } 
    if (shape1.getArea() > shape2.getArea()) {
      return 1;
    } 
    else {
      return 0;
    }
  }
  public int comparePeri (Shape shape1, Shape shape2) {
    if (shape1.getPerimeter() < shape2.getPerimeter()) {
      return -1;
    } 
    if (shape1.getPerimeter() > shape2.getPerimeter()) {
      return 1;
    } 
    else {
      return 0;
    }
  }

    
}
