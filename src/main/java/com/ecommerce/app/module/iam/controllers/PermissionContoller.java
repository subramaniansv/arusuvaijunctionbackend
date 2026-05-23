package com.ecommerce.app.module.iam.controllers;

import jakarta.servlet.http.*;

import com.ecommerce.app.module.iam.mapper.PermissionConverterUtil;
import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.models.Permission;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.iam.services.PermissionService;
import com.ecommerce.app.module.iam.util.SendResponseUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
@WebServlet("/api/permission")
@RequiresRole("Admin")
public class PermissionContoller extends HttpServlet{
    PermissionService service = new PermissionService();
    public void doPost(HttpServletRequest request ,HttpServletResponse response) throws IOException,ServletException {
        Permission permission = PermissionConverterUtil.requestToDto(request);
       permission =  service.create(permission);
       if(permission.getId() !=null){
        SendResponseUtil.sendResponse(new ApiResponse(true, "permission created", permission, 200), response);
       }else{
        SendResponseUtil.sendResponse(new ApiResponse(false, "permission not created", permission, 400), response);
       }
    }

    public void doGet(HttpServletRequest request ,HttpServletResponse response) throws IOException,ServletException{
        String raw = request.getParameter("permissionId");
        if(raw != null){
            Long permissionID;
            try {
                permissionID = Long.parseLong(raw);
            } catch (NumberFormatException nfe) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "invalid permissionId", null, 400), response);
                return;
            }
            Permission role = service.getPermissionById(permissionID);
            SendResponseUtil.sendResponse(new ApiResponse(true, "permission fetched", role, 200), response);
        }else{
            SendResponseUtil.sendResponse(new ApiResponse(true, "all permission fetched", service.getPermissions(), 200), response);
        }
    }

 public void doDelete(HttpServletRequest request ,HttpServletResponse response) throws IOException,ServletException{
        String raw = request.getParameter("permissionId");
        if(raw == null){
             SendResponseUtil.sendResponse(new ApiResponse(false, "id is required", null, 400), response);
             return;
        }
        Long permissionId;
        try {
            permissionId = Long.parseLong(raw);
        } catch (NumberFormatException nfe) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid permissionId", null, 400), response);
            return;
        }
        service.deletePermission(permissionId);
        SendResponseUtil.sendResponse(new ApiResponse(true, "permission deleted", null, 200), response);
    }

}
