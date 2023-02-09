package foresight;

import com.google.gson.Gson;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
public class ProjectManagementService
{
    static final String ITEM_TYPE_PROJECT = "PROJECT";
    static final String ITEM_TYPE_TASK = "TASK";

    private ItemsRepository itemsRepository;

    private Gson gson;

    @Autowired
    ProjectManagementService(ItemsRepository itemsRepository, Gson gson)
    {
        this.itemsRepository = itemsRepository;
        this.gson = gson;
    }

    public void uploadProject(String jsonFileName)
    {
        System.out.println("upload project. json file Name: " + jsonFileName);

        try
        {
            itemsRepository.deleteAll();

            File file = ResourceUtils.getFile("classpath:"+ jsonFileName);

            if (!file.exists())
            {
                System.out.println("File not Found! " + jsonFileName);
                return;
            }

            String json = new String(Files.readAllBytes(file.toPath()));
            JSONObject jsonObject = new JSONObject(json);
            JSONArray items = (JSONArray)jsonObject.get("items");
            JSONObject currentJsonItem;
            ArrayList<Item> itemsList = new ArrayList<>();

            for (int i = 0; i < items.length(); i++)
            {
                LocalDate startDate = null;
                LocalDate endDate = null;
                currentJsonItem = items.getJSONObject(i);
                String uid = currentJsonItem.get("uid").toString();
                String name = currentJsonItem.get("name").toString();
                String type = currentJsonItem.get("type").toString();
                String startDateStr = currentJsonItem.get("startDate").toString();
                String endDateStr = currentJsonItem.get("endDate").toString();
                String parentUid = currentJsonItem.get("parentUid").toString();
                if (type.equals(ITEM_TYPE_TASK))
                {
                    startDate = LocalDate.parse(startDateStr);
                    endDate = LocalDate.parse(endDateStr);
                }

                Item currentItem = new Item(uid, name, type, startDate, endDate, parentUid);
                itemsList.add(currentItem);
            }

            calculateStartEndDatesForProjectItems(itemsList);

            itemsRepository.saveAll(itemsList);

            System.out.println("uploaded items: ");
            itemsRepository.findAll().forEach(System.out::println);
        }
        catch(Exception e)
        {
            System.out.println("error during load json file: " + jsonFileName);
            e.printStackTrace();
        }
    }

    private void calculateStartEndDatesForProjectItems(List<Item> itemsList)
    {
        List<Item> projectItems =
                itemsList.stream().filter(item -> item.type.equals(ITEM_TYPE_PROJECT)).collect(Collectors.toList());

        projectItems.forEach(item -> calculateStartEndDatesSingleProjectItem(item, itemsList));
    }

    private void calculateStartEndDatesSingleProjectItem(Item currentItem, List<Item> itemsList)
    {
        Set<LocalDate> startDateSet = new HashSet<>();
        Set<LocalDate> endDateSet = new HashSet<>();

        getAllTaskItemChildDates(currentItem, itemsList, startDateSet, endDateSet);

        // sort start date set ==> first is the earliest start date
        List<LocalDate> startDateListSorted = startDateSet.stream().sorted().collect(Collectors.toList());
        LocalDate earliestStartDate = startDateListSorted.get(0);
        currentItem.setStartDate(earliestStartDate);

        // sort end date set ==> first is the latest end date
        List<LocalDate> endDateListSortedDescending = endDateSet.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        LocalDate latestEndDate = endDateListSortedDescending.get(0);
        currentItem.setEndDate(latestEndDate);
    }

    private void getAllTaskItemChildDates(Item currentItem, List<Item> itemsList, Set<LocalDate> startDateSet, Set<LocalDate> endDateSet)
    {
        List<Item> allItemChilds = new ArrayList<>();
        getAllItemChilds(currentItem, itemsList, allItemChilds);
        List<Item> allTaskItemChilds = allItemChilds.stream().filter(item -> item.getType().equals(ITEM_TYPE_TASK)).collect(Collectors.toList());

        allTaskItemChilds.forEach(item -> {
                startDateSet.add(item.getStartDate());
                endDateSet.add(item.getEndDate());
        });
    }

    private List<Item> getItemChilds(Item currentItem, List<Item> itemsList)
    {
        return itemsList.stream().filter(item -> item.getParentUid().equals(currentItem.getUid())).collect(Collectors.toList());
    }

    private void getAllItemChilds(Item currentItem, List<Item> itemsList, List<Item> allItemChilds)
    {
        List<Item> itemChilds =  itemsList.stream().filter(item -> item.getParentUid().equals(currentItem.getUid())).collect(Collectors.toList());
        allItemChilds.addAll(itemChilds);
        itemChilds.forEach(itemChild -> getAllItemChilds(itemChild, itemsList, allItemChilds));
    }

    public void addItem(String uid, String name, String type, String startDate, String endDate, String parentUid) throws Exception
    {
        LocalDate startLocalDate = null;
        LocalDate endLocalDate = null;

        if (type.equals(ITEM_TYPE_TASK))
        {
            startLocalDate = LocalDate.parse(startDate);
            endLocalDate = LocalDate.parse(endDate);
        }

        Item item = new Item(uid, name, type, startLocalDate, endLocalDate, parentUid);

        if (type.equals(ITEM_TYPE_PROJECT))
        {
            List<Item> itemsList = itemsRepository.findAll();

            long numChildTasks = getNumTasksAllChilds(item, itemsList);
            if (numChildTasks == 0)
            {
                throw new Exception("Error! no child tasks ==> not adding new item");
            }

            calculateStartEndDatesSingleProjectItem(item, itemsList);
        }

        System.out.println("add new item:" + item);

        itemsRepository.save(item);
    }

    private long getNumTasksAllChilds(Item item, List<Item> itemsList)
    {
        List<Item> allItemChilds = new ArrayList<>();
        getAllItemChilds(item, itemsList, allItemChilds);
        return allItemChilds.stream().filter(itemChild -> itemChild.getType().equals(ITEM_TYPE_TASK)).count();
    }

    public Item getItem(String uid) throws Exception
    {
        Item item = itemsRepository.findByUid(uid);
        if (item == null)
        {
            throw new Exception("Error: item with uid: " + uid + " doesn't exist");
        }

        return item;
    }

    public void deleteItem(String uid) throws Exception
    {
        Item item = getItem(uid);

        if (item.getParentUid() == null || item.getParentUid().equals("null"))
        {
            throw new Exception("Error: can't delete root item. uid: " + uid);
        }

        if (item.getType().equals(ITEM_TYPE_TASK))
        {
            deleteTaskItem(item);
        }
        else
        {
            deleteProjectItem(item);
        }
    }

    public void deleteTaskItem(Item item) throws Exception
    {
        // get parent for item
        Item parent = getItem(item.getParentUid());

        List<Item> itemsList = itemsRepository.findAll();
        long numChildTasks = getNumTasksAllChilds(parent, itemsList);

        // verify that task we are about to delete is not the single task of its parent project
        if (numChildTasks == 1)
        {
            throw new Exception("item with uid: " + item.getUid() + " is the only task child of its parent project ==> can't delete it");
        }

        itemsRepository.delete(item);

        System.out.println("task item with uid: " + item.getUid() +  " was deleted");
    }

    public void deleteProjectItem(Item item) throws Exception
    {
        List<Item> itemsList = itemsRepository.findAll();
        List<Item> itemChilds = getItemChilds(item, itemsList);
        String itemParentUid = item.getParentUid();

        // update all item childs to have its parent uid
        itemChilds.forEach(itemChild -> itemChild.setParentUid(itemParentUid));

        itemsRepository.delete(item);

        System.out.println("project item with uid: " + item.getUid() +  " was deleted");
    }

    public String getCompletionStatus(String uid, String date) throws Exception
    {
        Item item = getItem(uid);

        LocalDate inputDate = LocalDate.parse(date);
        LocalDate itemStartDate = item.getStartDate();
        LocalDate itemEndDate = item.getEndDate();

        if (inputDate.isBefore(itemStartDate) || inputDate.isEqual(itemStartDate))
        {
            return "0%";
        }

        if(inputDate.isAfter(itemEndDate))
        {
            return "100%";
        }

        long daysBetweenItemStartDateAndEndDate = DAYS.between(itemStartDate, itemEndDate) + 1; // +1 for including end date last day

        double daysBetweenInputDateAndStartDate = DAYS.between(itemStartDate, inputDate);
        int completionStatus = (int) ((daysBetweenInputDateAndStartDate / daysBetweenItemStartDateAndEndDate) * 100);

        return String.valueOf(completionStatus).concat("%");
    }

    public void updateStartDate(String uid, String date) throws Exception
    {
        Item item = getItem(uid);
        LocalDate inputStartDate = LocalDate.parse(date);
        if (item.getEndDate().isBefore(inputStartDate))
        {
            throw new Exception("item end date: " + item.getEndDate() + " is before input start date: " + inputStartDate + " ==> can't update");
        }

        item.setStartDate(inputStartDate);
        itemsRepository.save(item);
    }

    public void updateEndDate(String uid, String date) throws Exception
    {
        Item item = getItem(uid);
        LocalDate inputEndDate = LocalDate.parse(date);
        if (item.getStartDate().isAfter(inputEndDate))
        {
            throw new Exception("item start date: " + item.getStartDate() + " is after input end date: " + inputEndDate + " ==> can't update");
        }

        item.setEndDate(inputEndDate);
        itemsRepository.save(item);
    }


    public String getProjectHierarchy()
    {
        List<Item> itemsList = itemsRepository.findAll();
        return gson.toJson(itemsList);

    }

//    public void addItems(List<Item> items)
//    {
//        LocalDate startLocalDate = null;
//        LocalDate endLocalDate = null;
//
//        if (type.equals(ITEM_TYPE_TASK))
//        {
//            startLocalDate = LocalDate.parse(startDate);
//            endLocalDate = LocalDate.parse(endDate);
//        }
//    }

}

