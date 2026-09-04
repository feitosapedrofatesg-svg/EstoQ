package com.estoq.business.categoria;

import com.estoq.core.controllers.GenericController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController extends GenericController<CategoriaModel, CategoriaDTO, CategoriaService, CategoriaAdapter> {
}