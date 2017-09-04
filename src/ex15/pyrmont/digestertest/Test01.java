package ex15.pyrmont.digestertest;

import java.io.File;
import org.apache.commons.digester.Digester;
import org.junit.Test;

public class Test01 {

  public static void main(String[] args) {
    String path = System.getProperty("user.dir") + File.separator  + "etc";
    File file = new File(path, "employee1.xml");
    Digester digester = new Digester();
    // add rules
    digester.addObjectCreate("employee", "ex15.pyrmont.digestertest.Employee");//
    digester.addSetProperties("employee");//设置属性
//    digester.addCallMethod("employee", "printName");

    try {
      Employee employee = (Employee) digester.parse(file);
      System.out.println("First name : " + employee.getFirstName());
      System.out.println("Last name : " + employee.getLastName());
      employee.printName();
    }
    catch(Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testSystem() {
    System.out.println(System.getProperty("catalina.home"));
  }
  
}
