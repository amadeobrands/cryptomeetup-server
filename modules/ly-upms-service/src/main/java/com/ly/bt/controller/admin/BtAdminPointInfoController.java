package com.ly.bt.controller.admin;

import com.aliyun.oss.OSSClient;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.ly.admin.service.SysUserService;
import com.ly.bt.model.dto.BtLatlngMatchDTO;
import com.ly.bt.model.dto.BtPointInfoDTO;
import com.ly.bt.model.entity.BtPointGis;
import com.ly.bt.model.entity.BtPointInfo;
import com.ly.bt.service.BtPointGisService;
import com.ly.bt.service.BtPointInfoService;
import com.ly.common.bean.config.AliyunOssPropertiesConfig;
import com.ly.common.constant.CommonConstant;
import com.ly.common.util.GeoHashUtil;
import com.ly.common.util.Query;
import com.ly.common.util.R;
import com.ly.common.util.UserUtils;
import com.ly.common.util.exception.ParamsErrorException;
import com.ly.common.web.BaseController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 打开点详细信息 前端控制器
 * </p>
 *
 * @Author liyang
 * @Create 2018-11-16
 */
@Api(value = "管理员管理打卡点controller", tags = {"管理员管理打卡点-->管理员接口"})
@RestController
@RequestMapping("/bt/admin/point")
public class BtAdminPointInfoController extends BaseController {

    @Autowired
    private BtPointInfoService infoService;

    @Autowired
    private BtPointGisService gisService;


    @Autowired
    private SysUserService userService;

    @Autowired
    RestTemplate restTemplate;



    /**
     * 通过ID查询
     *
     * @param id ID
     * @return BtPointInfo
     */
//    @ApiOperation(value = "获取打卡点详细信息", notes = "ID查询")
//    @ApiImplicitParams({
//            @ApiImplicitParam(name = "id", value = "坐标ID", required = true, dataType = "int", paramType = "path")
//    })
    @GetMapping("/{id}")
    public R<BtPointInfo> get(@PathVariable Integer id) {
        BtPointInfo info = infoService.selectDetailByIdService(id);
        return new R(info);
    }


    /**
     * 打卡点列表
     *
     * @param page
     * @param status
     * @return
     */
    @ApiOperation(value = "查询打卡点", notes = "查询打卡点数据列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "page", value = "页", required = true, dataType = "int", paramType = "query"),
            @ApiImplicitParam(name = "limit", value = "一页数据条数", required = true, dataType = "int", paramType = "query"),
            @ApiImplicitParam(name = "status", value = "坐标状态", required = false, dataType = "int", paramType = "query")
    })
    @GetMapping("/list")
    public R<Page<BtPointInfo>> page(Integer page,
                                     @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
                                     @RequestParam(value = "status", required = false) String status) {

        Map params = new HashedMap();
        if (StringUtils.isNotEmpty(status)) {
            params.put("status", status);
        }

        params.put("page", page);
        params.put("limit", limit);
        params.put(CommonConstant.DEL_FLAG, CommonConstant.STATUS_NORMAL);
        Query query = new Query(params);
        EntityWrapper entityWrapper = new EntityWrapper<>();
        entityWrapper.orderBy("id",false);
        Page<BtPointInfo> page1 = infoService.selectPage(query, entityWrapper);
        if (page1.getRecords() != null && page1.getRecords().size() > 0) {
            for (BtPointInfo info : page1.getRecords()) {
                info.setUser(userService.selectUserBaseByIdService(info.getUserId()));
            }
        }
        return new R(page1);
    }


    @ApiOperation(value = "创建打卡点", notes = "创建打卡点")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "latitude", value = "坐标", required = true, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "longitude", value = "坐标", required = true, dataType = "String", paramType = "query")
    })
    @PostMapping("/create")
    public R<BtPointInfo> create(BtPointInfoDTO pointInfo, HttpServletRequest servletRequest) {

        if (GeoHashUtil.isRoundOut(pointInfo.getLatitude(), pointInfo.getLongitude())) {
            throw new ParamsErrorException("坐标不正确！");
        }
        pointInfo.setUserId(UserUtils.getUserId(servletRequest));

        return new R(infoService.createPointService(pointInfo, pointInfo.getLatitude(), pointInfo.getLongitude()));
    }


    @ApiOperation(value = "更新打卡点", notes = "更新打卡点")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "userId", value = "用户ID", required = true, dataType = "int", paramType = "header")
    })
    @PostMapping("/update")
    public R<Boolean> update(BtPointInfoDTO pointInfo) {
        if (pointInfo.getId() == null) {
            throw new ParamsErrorException();
        }
        pointInfo.setUpdateTime(new Date());
        boolean f = infoService.updateInfoService(pointInfo);
        return new R(f);
    }

    /**
     * 坐标列表分页
     */

    @ApiOperation(value = "查询坐标点", notes = "查询坐标点")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "page", value = "页", required = true, dataType = "int", paramType = "query")
    })
    @GetMapping("/gis/list")
    public R<Page<BtPointGis>> gisPage(@RequestParam Map<String, Object> params) {
        params.put(CommonConstant.DEL_FLAG, CommonConstant.STATUS_NORMAL);
        return new R(gisService.selectPage(new Query<>(params), new EntityWrapper<>()));
    }

    /**
     * @param id
     * @return
     */
    @ApiOperation(value = "删除坐标", notes = "删除坐标")
    @ApiImplicitParam(name = "id", value = "主键ID", required = true, dataType = "int", paramType = "query")
    @DeleteMapping("/gis/del")
    public R<Boolean> gisDel(Integer id) {
        BtPointGis pointGis = gisService.selectById(id);
        if (pointGis == null) {
            throw new ParamsErrorException();
        }
        pointGis.setDelFlag(CommonConstant.STATUS_DEL);
        gisService.updateById(pointGis);
        return new R<>(true);
    }

    @ApiOperation(value = "删除打卡点", notes = "删除打卡点")
    @ApiImplicitParam(name = "id", value = "主键ID", required = true, dataType = "int", paramType = "query")
    @DeleteMapping("/del")
    public R<Boolean> del(Integer id) {
        BtPointInfo btPointInfo = infoService.selectById(id);
        if (btPointInfo == null) {
            throw new ParamsErrorException();
        }
        btPointInfo.setDelFlag(CommonConstant.STATUS_DEL);
//        infoService.deleteById(btPointInfo.getId());
        infoService.updateById(btPointInfo);

        BtPointGis gis = gisService.selectById(btPointInfo.getGisId());
        gis.setDelFlag(CommonConstant.STATUS_DEL);
//        gisService.deleteById(btPointInfo.getGisId());
        gisService.updateById(gis);

        return new R<>(true);
    }

    /**
     * 打卡点换人
     *
     * @param userId
     * @param id
     * @return
     */
    @PostMapping("/change/user")
    public R changeUser(@RequestParam("userId") Integer userId, @RequestParam("id") Integer id) {

        BtPointInfo info = infoService.selectById(id);

        info.setUserId(userId);
        infoService.updateById(info);

        return new R(true);
    }

    /**
     * 更新上链ID
     * @param dappId
     * @param id
     * @return
     */
    @PostMapping("/update/dapp")
    public R updateDappId(@RequestParam("dappId") String dappId, @RequestParam("id") Integer id) {

        BtPointInfo info = infoService.selectById(id);
        info.setDappId(dappId);
        info.setUpdateTime(new Date());
        infoService.updateById(info);

        return new R(true);
    }

    /**
     * match latlng in system database
     * @param latlngs
     * @return
     */
    @PostMapping("/match/latlng")
    public R matchLatLng(@RequestBody List<BtLatlngMatchDTO> latlngs){


        for(BtLatlngMatchDTO latLng : latlngs){
            EntityWrapper<BtPointInfo> wrapper = new EntityWrapper<>();
            wrapper.eq(BtPointInfo.LATITUDE,"");
            wrapper.eq(BtPointInfo.LONGITUDE,"");
            latLng.setMatch(infoService.selectList(wrapper).size()>0);
        }


        return new R(latlngs);
    }

    /**
     * create list latlng
     * @param pointInfo
     * @return
     */
    @PostMapping("/create/list/point")
    public R createMatchLatLng(@RequestBody BtPointInfoDTO pointInfo,HttpServletRequest request){

        if(StringUtils.isEmpty(pointInfo.getImageUrl())){
            throw new ParamsErrorException("params error!");
        }

        byte[] bytes = restTemplate.getForObject(pointInfo.getImageUrl(),byte[].class);
        InputStream sbs = new ByteArrayInputStream(bytes);

        R<String> r = upload(sbs);

        String images = String.format("[{\"url\":\"//cryptomeetup-img.oss-cn-shanghai.aliyuncs.com/%s?x-oss-process=style/cover-small\",\"path\":\"%s\"}]",r.getData(),r.getData());

        BtPointInfo info = new BtPointInfo();
        info.setLongitude(pointInfo.getLongitude());
        info.setLatitude(pointInfo.getLatitude());
        info.setImages(images);
        info.setTitle(pointInfo.getTitle());
        info.setDes(pointInfo.getDes());

        if(pointInfo.getUserId()==null||pointInfo.getUserId()==0){
            info.setUserId(UserUtils.getUserId(request));
        }else {
            info.setUserId(pointInfo.getUserId());
        }

        info.setImages(images);


        infoService.createPointService(info,pointInfo.getLatitude(),pointInfo.getLongitude());
        return new R();
    }


    @Autowired
    private AliyunOssPropertiesConfig ossPropertiesConfig;


    private R<String> upload(InputStream file) {
        String url = ossPropertiesConfig.createImagePath();
        // 创建OSSClient实例。
        OSSClient ossClient = new OSSClient(ossPropertiesConfig.getEndpoint(), ossPropertiesConfig.getSecretId(), ossPropertiesConfig.getSecretKey());
        // 上传网络流。
        ossClient.putObject(ossPropertiesConfig.getBucketName(), url, file);
        // 关闭OSSClient。
        ossClient.shutdown();
        return new R<>(url);
    }


}
