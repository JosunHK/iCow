# This is not being developed anymore

## A simple toy ORM written in Java for excel database

### Table Definition are similar to Hibernate's

@Table / @SecondaryTable to define a table
@Id to define a primary key
@Column to define a column

###  How It Works

After defining a table, the library will generate a xlsx file
for you that represents that table. You can then use the
library to execute simple CRUD operations on that table.

###  Example
private <T> void getListFromExcel(Class<T> clazz){},
public <T> void insert(T obj){}
.......

see the demos for more exmaple
