package BDClass;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.poi.ss.formula.functions.T;
import org.apache.poi.ss.formula.functions.MultiOperandNumericFunction.Policy;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import dataType.individual.Person;

public class DataBaseUtil{

    //init
    private Map<Class,List<?>> map = new HashMap<>();

    private static DataBaseUtil instance = null;

    /*********************************************** for manager classes to use ****************************************************/ 
    private DataBaseUtil(){
    }

    public static DataBaseUtil getInstance(){
        if(instance == null) instance = new DataBaseUtil();
        return instance;
    }

    public <T> List<T> getList(Class<T> clazz){
        List<T> dataList = (List<T>) map.get(clazz);
        if(dataList == null) getListFromExcel(clazz);
        dataList = (List<T>) map.get(clazz);
        return dataList;
    }

    /*********************************************** update obj in DataBase ****************************************************/ 

    public <T> void update(T obj){
        update(obj, null);
    }

    private <T> void update(T obj, Class clazz){

        if(clazz == null) clazz = obj.getClass();
        List<?> objList = getList(clazz);
        Field idField = null;

        //check if class is table
        boolean isTable = clazz.isAnnotationPresent(Table.class) || clazz.isAnnotationPresent(SecondaryTable.class);
        boolean isSecondaryTable = clazz.isAnnotationPresent(SecondaryTable.class);
        List<Field> fieldList = new ArrayList<>();

        if(!isTable) return; // should throws erorr instead

        //get all fields with annotation in clazz
        for(Field field: clazz.getDeclaredFields()){
            field.setAccessible(true);
            if(field.isAnnotationPresent(Column.class)){
                fieldList.add(field);
            }
            //get the Id field from clazz
            if(field.isAnnotationPresent(Id.class)){
                idField = field;
            }
        }

        //get idField
        if(idField == null){
            idField = getIdFieldForClass(clazz);
        }
        
        //throw exception if no @Id is present in class
        //should write a new set of exception, but am too lazy 
        if(idField == null){
            System.out.println("this class has no field annotated with @Id");
            throw new NullPointerException();
        }

        //get from excel if cache does not exist
        if(objList == null) {
            getListFromExcel(clazz);
            objList = getList(clazz);
        }
        
        try{
            int index = -1;

            //get original object with that id from the objlist
            for(int i = 0;i<objList.size();i++){
                Object o = objList.get(i);
                PropertyDescriptor pd = new PropertyDescriptor(idField.getName(), clazz);
                Method getter = pd.getReadMethod();
                int oIdCurr = Integer.class.cast(getter.invoke(o));// Id of object from objectlist
                int oId = Integer.class.cast(getter.invoke(obj)); // Id of object to be updated
                if(oIdCurr == oId) index = i;

            }
            
            //again should write new exception set
            if(index<0) throw new NullPointerException();

            String file_name = "db\\" + clazz.getSimpleName() + ".xlsx";
            FileInputStream excelFile = new FileInputStream(new File(file_name));
            XSSFWorkbook workbook = new XSSFWorkbook(excelFile);
            Sheet dataSheet = workbook.getSheetAt(0);

            Row row = dataSheet.getRow(index);

            for(int i = 0; i < fieldList.size();i++){
                Field f = fieldList.get(i);

                PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                Method getter = pd.getReadMethod();
                Cell cell = isSecondaryTable ? row.getCell(i + 1) : row.getCell(i);
                Class c = f.getType();
                
                 //set cell value as string
                if(String.class.isAssignableFrom(c)){
                    cell.setCellValue(String.class.cast(getter.invoke(obj)));
                }

                //set cell value as int
                else if(Integer.class.isAssignableFrom(c) || int.class.isAssignableFrom(c)){
                    cell.setCellValue(Integer.class.cast(getter.invoke(obj)));
                }

                //set cell value as double
                else if(double.class.isAssignableFrom(c)){
                    cell.setCellValue(double.class.cast(getter.invoke(obj)));
                }

                //set cell value as Date
                else if(Date.class.isAssignableFrom(c)){
                    cell.setCellValue(Date.class.cast(getter.invoke(obj)));
                }

                //set cell value as boolean
                else if(boolean.class.isAssignableFrom(c)){
                    cell.setCellValue(boolean.class.cast(getter.invoke(obj)));
                }

                else{
                    System.out.println("no type matched that field");
                }
            }

            FileOutputStream outputStream = new FileOutputStream(file_name);
            workbook.write(outputStream);
            workbook.close();

            if(isSecondaryTable) update(obj, clazz.getSuperclass());

            //update cache
            getListFromExcel(clazz);


        }catch(Exception e){
            System.out.println(e);
        }
    }

    private <T> void updateForSecondaryTable(T obj){
        
    }

    /*********************************************** insert obj into DataBase ****************************************************/ 

    //overloading 
    public <T> void insert(T obj){
        insert(obj, null);
    }

    private <T> void insert(T obj, Class clazz){

        if(clazz == null) clazz = obj.getClass();
        List<?> objList = getList(clazz);

        //check if class is table
        boolean isTable = clazz.isAnnotationPresent(Table.class) || clazz.isAnnotationPresent(SecondaryTable.class);
        boolean isSecondaryTable = clazz.isAnnotationPresent(SecondaryTable.class);
        List<Field> fieldList = new ArrayList<>();

        if(!isTable) return; // should throws erorr instead
        if(isSecondaryTable){
            insertForSecondaryTable(obj, clazz);
            return;
        }

        //get all fields with annotation in clazz
        for(Field field: clazz.getDeclaredFields()){
            field.setAccessible(true);
            if(field.isAnnotationPresent(Column.class)){
                fieldList.add(field);
            }
        }

        //get from excel if cache does not exist
        if(objList == null) {
            getListFromExcel(clazz);
            objList = getList(clazz);
        }

        try{
            String file_name = "db\\" + clazz.getSimpleName() + ".xlsx";
            FileInputStream excelFile = new FileInputStream(new File(file_name));
            XSSFWorkbook workbook = new XSSFWorkbook(excelFile);
            Sheet dataSheet = workbook.getSheetAt(0);

            Row row = dataSheet.createRow(objList.size());

            for(int i = 0; i < fieldList.size();i++){
                Field f = fieldList.get(i);

                PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                Method getter = pd.getReadMethod();
                Cell cell = row.createCell(i);
                Class c = f.getType();
                
                 //set cell value as string
                if(String.class.isAssignableFrom(c)){
                    cell.setCellValue(String.class.cast(getter.invoke(obj)));
                }

                //set cell value as int
                else if(Integer.class.isAssignableFrom(c) || int.class.isAssignableFrom(c)){
                    cell.setCellValue(Integer.class.cast(getter.invoke(obj)));
                }

                //set cell value as double
                else if(double.class.isAssignableFrom(c)){
                    cell.setCellValue(double.class.cast(getter.invoke(obj)));
                }

                //set cell value as Date
                else if(Date.class.isAssignableFrom(c)){
                    cell.setCellValue(Date.class.cast(getter.invoke(obj)));
                }

                //set cell value as boolean
                else if(boolean.class.isAssignableFrom(c)){
                    cell.setCellValue(boolean.class.cast(getter.invoke(obj)));
                }

                else{
                    System.out.println("no type matched that field");
                }
            }

            FileOutputStream outputStream = new FileOutputStream(file_name);
            workbook.write(outputStream);
            workbook.close();

            //update cache
            getListFromExcel(clazz);

        }catch(Exception e){
            System.out.println(e);
        }
    }

    private <T> void insertForSecondaryTable(T obj, Class clazz){

        List<?> objList = getList(clazz);

        //check if class is table
        List<Field> fieldList = new ArrayList<>();

        //get all fields with annotation in clazz
        for(Field field: clazz.getDeclaredFields()){
            field.setAccessible(true);
            if(field.isAnnotationPresent(Column.class)){
                fieldList.add(field);
            }
        }

        //get from excel if cache does not exist
        if(objList == null) {
            getListFromExcel(clazz);
            objList = getList(clazz);
        }

        //get idField
        Field idField = getIdFieldForClass(clazz);


        try{
            String file_name = "db\\" + clazz.getSimpleName() + ".xlsx";
            FileInputStream excelFile = new FileInputStream(new File(file_name));
            XSSFWorkbook workbook = new XSSFWorkbook(excelFile);
            Sheet dataSheet = workbook.getSheetAt(0);

            Row row = dataSheet.createRow(objList.size());

            Cell idCell = row.createCell(0);
            PropertyDescriptor idpd = new PropertyDescriptor(idField.getName(), clazz);
            Method idGetter = idpd.getReadMethod();
            idCell.setCellValue(Integer.class.cast(idGetter.invoke(obj)));


            for(int i = 0; i < fieldList.size();i++){
                Field f = fieldList.get(i);

                PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                Method getter = pd.getReadMethod();
                Cell cell = row.createCell(i+1);
                Class c = f.getType();
                
                 //set cell value as string
                if(String.class.isAssignableFrom(c)){
                    cell.setCellValue(String.class.cast(getter.invoke(obj)));
                }

                //set cell value as int
                else if(Integer.class.isAssignableFrom(c) || int.class.isAssignableFrom(c)){
                    cell.setCellValue(Integer.class.cast(getter.invoke(obj)));
                }

                //set cell value as double
                else if(double.class.isAssignableFrom(c)){
                    cell.setCellValue(double.class.cast(getter.invoke(obj)));
                }

                //set cell value as Date
                else if(Date.class.isAssignableFrom(c)){
                    cell.setCellValue(Date.class.cast(getter.invoke(obj)));
                }

                //set cell value as boolean
                else if(boolean.class.isAssignableFrom(c)){
                    cell.setCellValue(boolean.class.cast(getter.invoke(obj)));
                }

                else{
                    System.out.println("no type matched that field");
                }
            }

            FileOutputStream outputStream = new FileOutputStream(file_name);
            workbook.write(outputStream);
            workbook.close();

            insert(obj, clazz.getSuperclass());

            //update cache
            getListFromExcel(clazz);

        }catch(Exception e){
            System.out.println(e);
        }
    }

    /*********************************************** get List from DataBase functions ****************************************************/

    private <T> void getListFromExcel(Class<T> clazz){

        //check if class is table
        boolean isTable = clazz.isAnnotationPresent(Table.class) || clazz.isAnnotationPresent(SecondaryTable.class);
        boolean isSecondaryTable = clazz.isAnnotationPresent(SecondaryTable.class);
        List<Field> fieldList = new ArrayList<>();

        if(!isTable) return; // should throws erorr instead

        //special handling for datatypes that extends
        if(isSecondaryTable){ 
            getListFromExcelForSecondaryTable(clazz);
            return;
        }

        //get all fields with annotation in clazz
        for(Field field: clazz.getDeclaredFields()){
            field.setAccessible(true);
            if(field.isAnnotationPresent(Column.class)){
                fieldList.add(field);
            }
        }

        //start creating entity list from excel
        try{
            //read workbook
            String file_name = "db\\" + clazz.getSimpleName() + ".xlsx";
            FileInputStream excelFile = new FileInputStream(new File(file_name));
            XSSFWorkbook workbook = new XSSFWorkbook(excelFile);
            Sheet dataSheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = dataSheet.iterator();

            List<T> dataList = new ArrayList<>();

            //looping the rows
            while (iterator.hasNext()) {

                Row currentRow = iterator.next();
                boolean isEnd = true;

                //creates object
                T obj = clazz.getDeclaredConstructor().newInstance();

                //looping cells
               for(int i = 0; i < fieldList.size();i++) {

                    Cell currentCell =  currentRow.getCell(i, MissingCellPolicy.RETURN_BLANK_AS_NULL);

                    if(currentCell == null) continue;
                    isEnd = false;

                    Field f = fieldList.get(i);

                    Class c = f.getType();
                    Object cellData = null;

                    //get cell value as string
                    if(String.class.isAssignableFrom(c)){
                        cellData = currentCell.getStringCellValue();
                        PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                        Method setter = pd.getWriteMethod();
                        setter.invoke(obj, f.getType().cast(cellData));
                    }

                    //get cell value as int
                    else if(int.class.isAssignableFrom(c)){
                        cellData = currentCell.getNumericCellValue();
                        PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                        Method setter = pd.getWriteMethod();
                        setter.invoke(obj, ((Double)cellData).intValue());
                    }

                    //get cell value as double
                    else if(double.class.isAssignableFrom(c)){
                        cellData = currentCell.getNumericCellValue();
                        PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                        Method setter = pd.getWriteMethod();
                        setter.invoke(obj, cellData);
                    }

                    //get cell value as Date
                    else if(Date.class.isAssignableFrom(c)){
                        cellData = currentCell.getDateCellValue();
                        PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                        Method setter = pd.getWriteMethod();
                        setter.invoke(obj, f.getType().cast(cellData));
                    }

                    //get cell value as boolean
                    else if(boolean.class.isAssignableFrom(c)){
                        cellData = currentCell.getBooleanCellValue();
                        PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                        Method setter = pd.getWriteMethod();
                        setter.invoke(obj, f.getType().cast(cellData));
                    }

                    else{
                        System.out.println("no type matched that field");
                        throw new ClassCastException(); 
                    }
                }

                if(isEnd) break; // ends looping if no more cells

                dataList.add(obj);
            }

            map.put(clazz, dataList); // put to map as cache
            workbook.close();

        }catch(Exception e){
            System.out.println(e);
        }
    }

    private <T> void getListFromExcelForSecondaryTable(Class<T> clazz){
        
        List<Field> fieldList = new ArrayList<>();

        //get all fields with annotation in clazz
        for(Field field: clazz.getDeclaredFields()){
            field.setAccessible(true);
            if(field.isAnnotationPresent(Column.class)){
                fieldList.add(field);
            }
        }

        //start creating entity list from excel
        try{
            //read workbook
            String file_name = "db\\" + clazz.getSimpleName() + ".xlsx";
            FileInputStream excelFile = new FileInputStream(new File(file_name));
            XSSFWorkbook workbook = new XSSFWorkbook(excelFile);
            Sheet dataSheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = dataSheet.iterator();
            Map<Integer, T> objectMap = new HashMap<>();
            List<T> finalList = new ArrayList<>();

            //get data list of super class
            List<?> tempList = null;
            tempList = map.get(clazz.getSuperclass());
            
            //init if not yet exists
            if(tempList == null) getListFromExcel(clazz.getSuperclass());
            tempList = map.get(clazz.getSuperclass());

            //cast superclass list to subclass list
            List<T> dataList = new ArrayList<>();
            tempList.forEach(temp->{
    
                try {
                    T obj = clazz.getDeclaredConstructor().newInstance();
                    BeanUtils.copyProperties(obj,temp);
                    dataList.add(obj);
                } catch (Exception e) {}
    
            });

            //get idField
            Field idField = getIdFieldForClass(clazz);

            //collecting all ids of super class as a list
            for(T obj : dataList){
                PropertyDescriptor pd = new PropertyDescriptor(idField.getName(), clazz);
                Method getter = pd.getReadMethod();
                objectMap.put(Integer.class.cast(getter.invoke(obj)), obj);
            }
        
            //looping the rows
            while (iterator.hasNext()) {

                Row currentRow = iterator.next();

                int id = 0;

                //checks if the the id of the row obj we're about the get even exist in the super class list
                try{
                    Cell idCell = currentRow.getCell(0, MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    id = ((Double)idCell.getNumericCellValue()).intValue();
                }catch(Exception e){break;} // end if can't get id

                if(!objectMap.keySet().contains(id)) continue; //go next if not exists;

                //creates object
                T obj = objectMap.get(id);

                //looping cells
               for(int i = 0; i < fieldList.size();i++) {

                   Cell currentCell = null;

                    currentCell = currentRow.getCell(i+1, MissingCellPolicy.RETURN_BLANK_AS_NULL); // starting from +1 since 0 is for id

                    Field f = fieldList.get(i);

                    Class c = f.getType();
                    Object cellData = null;

                    //get cell value as string
                    if(String.class.isAssignableFrom(c)){
                        cellData = currentCell.getStringCellValue();
                        PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                        Method setter = pd.getWriteMethod();
                        setter.invoke(obj, f.getType().cast(cellData));
                    }

                    //get cell value as int
                    else if(int.class.isAssignableFrom(c)){
                        cellData = currentCell.getNumericCellValue();
                        PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                        Method setter = pd.getWriteMethod();
                        setter.invoke(obj, ((Double)cellData).intValue());
                    }

                    //get cell value as double
                    else if(double.class.isAssignableFrom(c)){
                        cellData = currentCell.getNumericCellValue();
                        PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                        Method setter = pd.getWriteMethod();
                        setter.invoke(obj, cellData);
                    }

                    //get cell value as Date
                    else if(Date.class.isAssignableFrom(c)){
                        cellData = currentCell.getDateCellValue();
                        PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                        Method setter = pd.getWriteMethod();
                        setter.invoke(obj, f.getType().cast(cellData));
                    }

                    //get cell value as boolean
                    else if(boolean.class.isAssignableFrom(c)){
                        cellData = currentCell.getBooleanCellValue();
                        PropertyDescriptor pd = new PropertyDescriptor(f.getName(), clazz);
                        Method setter = pd.getWriteMethod();
                        setter.invoke(obj, f.getType().cast(cellData));
                    }

                    else{
                        System.out.println("no type matched that field");
                        throw new ClassCastException();
                    }
                }

                finalList.add(obj);
            }

            map.put(clazz, finalList); // put to map as cache
            workbook.close();

        }catch(Exception e){
            System.out.println(e);
        }
    }
    
    /*********************************************** helper / common functions ****************************************************/

    //get the lowest-level superClass for clazz
    private Field getIdFieldForClass(Class clazz){

        Field idField = null;
        //check if class is table
        boolean isTable = clazz.isAnnotationPresent(Table.class) || clazz.isAnnotationPresent(SecondaryTable.class);
        boolean isSecondaryTable = clazz.isAnnotationPresent(SecondaryTable.class);

        if(!isTable) return null; // should throws erorr instead

        //try getting it from super class instead 
        if(isSecondaryTable) return getIdFieldForClass(clazz.getSuperclass());

        //getting idField
        for(Field field: clazz.getDeclaredFields()){
            field.setAccessible(true);

            //get the Id field from clazz
            if(field.isAnnotationPresent(Id.class)){
                idField = field;
            }
        }

        return idField;
    }
}
