package foresight;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/project")
public class ProjectManagementController
{
    ProjectManagementService projectManagementService;

    @Autowired
    ProjectManagementController(ProjectManagementService projectManagementService)
    {
        this.projectManagementService = projectManagementService;
    }

    @PostMapping("/uploadProject/{jsonFileName}/")
    public void uploadProject(@PathVariable String jsonFileName)
    {
        projectManagementService.uploadProject(jsonFileName);
    }

    @PostMapping("/addItem/{uid}/{name}/{type}/{parentUid}/{startDate}/{endDate}/")
    public void addItem(@PathVariable String uid, @PathVariable String name, @PathVariable String type,
                        @PathVariable String startDate, @PathVariable String endDate,
                        @PathVariable String parentUid) throws Exception
    {
        projectManagementService.addItem(uid, name, type, startDate, endDate, parentUid);
    }

    @PostMapping("/addItem/{uid}/{name}/{type}/{parentUid}/")
    public void addItem(@PathVariable String uid, @PathVariable String name, @PathVariable String type,
                        @PathVariable String parentUid) throws Exception
    {
        projectManagementService.addItem(uid, name, type, null, null, parentUid);
    }

//    @PostMapping("/campaigns/addItems")
//    public void addItems(@RequestBody List<Item> items) throws Exception
//    {
//        campaignsService.addItems(items);
//    }

    @GetMapping("/getItem/{uid}")
    public Item getItem(@PathVariable String uid) throws Exception
    {
        return projectManagementService.getItem(uid);
    }

    @PostMapping("/deleteItem/{uid}")
    public void deleteItem(@PathVariable String uid) throws Exception
    {
        projectManagementService.deleteItem(uid);
    }

    @GetMapping("/getCompletionStatus/{uid}/{date}/")
    public String getCompletionStatus(@PathVariable String uid, @PathVariable String date) throws Exception
    {
        return projectManagementService.getCompletionStatus(uid, date);
    }

    @PostMapping("/updateStartDate/{uid}/{date}/")
    public void updateStartDate(@PathVariable String uid, @PathVariable String date) throws Exception
    {
        projectManagementService.updateStartDate(uid, date);
    }

    @PostMapping("/updateEndDate/{uid}/{date}/")
    public void updateEndDate(@PathVariable String uid, @PathVariable String date) throws Exception
    {
        projectManagementService.updateEndDate(uid, date);
    }

    @GetMapping("/getProjectHierarchy/")
    public List<Item> getProjectHierarchy()
    {
        return projectManagementService.getProjectHierarchy();
    }
}

