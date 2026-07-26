package com.yicaitong;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api/images") @RequiredArgsConstructor
class ImageController {
  private final CatalogController catalog; private final ProductImageRepository images; private final ProductRepository products;
  private final MediaService media; private final VectorService vectors;

  @PostMapping("/products/{productId}") ProductDto upload(@PathVariable UUID productId,@RequestPart MultipartFile file) throws Exception {
    UserContext.require(Domain.Role.ADMIN); Product p=catalog.owned(productId);
    validate(file); ProductImage pi=new ProductImage(); pi.setTenantId(UserContext.get().tenantId()); pi.setProductId(productId);
    pi.setObjectKey("tenant/"+pi.getTenantId()+"/product/"+productId+"/"+UUID.randomUUID()+".jpg"); media.put(file,pi.getObjectKey()); images.save(pi);
    try { List<Double> v=vectors.embed(file.getBytes(),file.getContentType()); vectors.index(pi.getId(),pi.getTenantId(),productId,v); pi.setVectorStatus("READY"); pi.setModelVersion("siglip"); }
    catch(Exception e){pi.setVectorStatus("FAILED");} images.save(pi); return catalog.dto(p);
  }
  @PostMapping("/search") List<Map<String,Object>> search(@RequestPart MultipartFile file,@RequestParam(defaultValue="20") int top) throws Exception {
    validate(file); List<Map<String,Object>> hits=vectors.search(UserContext.get().tenantId(),vectors.embed(file.getBytes(),file.getContentType()),Math.min(top,20));
    Map<UUID,ProductDto> map=new HashMap<>(); products.findByTenantIdAndDeletedFalseAndOnSaleTrueOrderByIdDesc(UserContext.get().tenantId()).forEach(p->map.put(p.getId(),catalog.dto(p)));
    return hits.stream().map(h->{UUID id=UUID.fromString((String)h.get("productId"));return Map.<String,Object>of("product",map.get(id),"similarity",h.get("similarity"));}).filter(x->x.get("product")!=null).toList();
  }
  void validate(MultipartFile f){if(f.isEmpty()||f.getContentType()==null||!f.getContentType().startsWith("image/"))throw new IllegalArgumentException("只允许上传图片");}
}
