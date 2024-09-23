package User;

import java.util.Date;

import BDClass.Column;
import BDClass.Id;
import BDClass.Table;

@Table
public class Person {
    @Id
    @Column
    private int pid;

    @Column
    private String name;
    
    public Person(){}
    
    public Person(int pid, String name){
        this.pid = pid;
        this.name = name;
    }
    
    public int getPid() {
        return this.pid;
    }
    
    public void setPid(int pid) {
        this.pid = pid;
    }
    
    public String getName() {
        return this.name;
    }
    
    public void setName(String name) {
        this.name = name;
	}
}
